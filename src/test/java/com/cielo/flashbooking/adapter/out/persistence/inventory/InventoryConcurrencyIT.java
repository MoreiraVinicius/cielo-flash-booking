package com.cielo.flashbooking.adapter.out.persistence.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.inventory.application.InventoryOperations;
import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class InventoryConcurrencyIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private InventoryOperations inventory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearEvents() {
        jdbcTemplate.update("UPDATE executive_summary_control SET enabled = FALSE, enabled_at = NULL WHERE id = TRUE");
        jdbcTemplate.update("DELETE FROM event");
    }

    @Test
    void decrementsOnlyWhenCapacityIsSufficient() {
        UUID id = insertEvent(10, 3);

        assertThat(inventory.decrement(id, 2)).isPresent();
        assertThat(available(id)).isEqualTo(1);
        assertThat(inventory.decrement(id, 2)).isEmpty();
        assertThat(available(id)).isEqualTo(1);
    }

    @Test
    void incrementsWithoutExceedingTotalCapacity() {
        UUID id = insertEvent(10, 7);

        assertThat(inventory.increment(id, 3)).isTrue();
        assertThat(available(id)).isEqualTo(10);
        assertThat(inventory.increment(id, 1)).isFalse();
        assertThat(available(id)).isEqualTo(10);
    }

    @Test
    void neverOversellsUnderConcurrentDecrements() throws Exception {
        UUID id = insertEvent(100, 100);
        var executor = Executors.newFixedThreadPool(20);
        var attempts = new ArrayList<java.util.concurrent.Callable<Optional<Instant>>>();
        for (int index = 0; index < 200; index++) {
            attempts.add(() -> inventory.decrement(id, 1));
        }

        try {
            long accepted = executor.invokeAll(attempts).stream()
                    .filter(future -> result(future).isPresent())
                    .count();

            assertThat(accepted).isEqualTo(100);
            assertThat(available(id)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refusesDecrementBeforeStartAndAtOrAfterEnd() {
        UUID id = insertEvent(10, 10);
        jdbcTemplate.update("UPDATE event SET starts_at = clock_timestamp() + interval '10 minutes' WHERE id = ?", id);

        assertThat(inventory.decrement(id, 1)).isEmpty();
        assertThat(available(id)).isEqualTo(10);

        jdbcTemplate.update("""
                UPDATE event
                SET created_at = clock_timestamp() - interval '11 minutes', starts_at = NULL, ends_at = clock_timestamp()
                WHERE id = ?
                """, id);
        assertThat(inventory.decrement(id, 1)).isEmpty();
        assertThat(available(id)).isEqualTo(10);
    }

    @Test
    void recordsFirstZeroOnlyWhileGloballyEnabledAndDoesNotReplaceItAfterRestock() {
        UUID id = insertEvent(10, 1);
        assertThat(inventory.decrement(id, 1)).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT first_available_zero_at FROM event WHERE id = ?", java.sql.Timestamp.class, id))
                .isNull();

        jdbcTemplate.update(
                "UPDATE executive_summary_control SET enabled = TRUE, enabled_at = clock_timestamp() WHERE id = TRUE");
        assertThat(inventory.increment(id, 1)).isTrue();
        Instant acceptedAt = inventory.decrement(id, 1).orElseThrow();
        Instant firstZero = jdbcTemplate
                .queryForObject("SELECT first_available_zero_at FROM event WHERE id = ?", java.sql.Timestamp.class, id)
                .toInstant();
        assertThat(firstZero).isBeforeOrEqualTo(acceptedAt);

        assertThat(inventory.increment(id, 1)).isTrue();
        inventory.decrement(id, 1).orElseThrow();
        assertThat(jdbcTemplate
                        .queryForObject(
                                "SELECT first_available_zero_at FROM event WHERE id = ?", java.sql.Timestamp.class, id)
                        .toInstant())
                .isEqualTo(firstZero);
    }

    @Test
    @Timeout(value = 15, unit = java.util.concurrent.TimeUnit.SECONDS)
    void returnsDatabaseAcceptanceTimeAfterWaitingForEventRowLock() throws Exception {
        UUID id = insertEvent(10, 1);
        var lockAcquired = new java.util.concurrent.CountDownLatch(1);
        var releaseLock = new java.util.concurrent.CountDownLatch(1);
        var lockTime = new java.util.concurrent.atomic.AtomicReference<Instant>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var holder =
                    executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject("SELECT id FROM event WHERE id = ? FOR UPDATE", UUID.class, id);
                        lockTime.set(jdbcTemplate
                                .queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class)
                                .toInstant());
                        lockAcquired.countDown();
                        try {
                            releaseLock.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(exception);
                        }
                    }));
            assertThat(lockAcquired.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();
            var request = executor.submit(() -> inventory.decrement(id, 1).orElseThrow());
            Thread.sleep(500);
            releaseLock.countDown();
            holder.get(5, java.util.concurrent.TimeUnit.SECONDS);
            Instant acceptedAt = request.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(Duration.between(lockTime.get(), acceptedAt)).isGreaterThanOrEqualTo(Duration.ofMillis(450));
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    private Optional<Instant> result(java.util.concurrent.Future<Optional<Instant>> future) {
        try {
            return future.get();
        } catch (Exception failure) {
            throw new AssertionError("concurrent inventory update failed", failure);
        }
    }

    private UUID insertEvent(int capacity, int available) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available) VALUES (?, 'Inventory event', ?, ?)",
                id,
                capacity,
                available);
        return id;
    }

    private int available(UUID id) {
        return jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, id);
    }
}
