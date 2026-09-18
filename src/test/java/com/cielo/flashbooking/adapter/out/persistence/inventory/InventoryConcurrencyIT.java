package com.cielo.flashbooking.adapter.out.persistence.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.inventory.application.InventoryOperations;
import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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

    @BeforeEach
    void clearEvents() {
        jdbcTemplate.update("DELETE FROM event");
    }

    @Test
    void decrementsOnlyWhenCapacityIsSufficient() {
        UUID id = insertEvent(10, 3);

        assertThat(inventory.decrement(id, 2)).isTrue();
        assertThat(available(id)).isEqualTo(1);
        assertThat(inventory.decrement(id, 2)).isFalse();
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
        var attempts = new ArrayList<java.util.concurrent.Callable<Boolean>>();
        for (int index = 0; index < 200; index++) {
            attempts.add(() -> inventory.decrement(id, 1));
        }

        try {
            long accepted = executor.invokeAll(attempts).stream()
                    .filter(future -> result(future))
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

        assertThat(inventory.decrement(id, 1)).isFalse();
        assertThat(available(id)).isEqualTo(10);

        jdbcTemplate.update("""
                UPDATE event
                SET created_at = clock_timestamp() - interval '11 minutes', starts_at = NULL, ends_at = clock_timestamp()
                WHERE id = ?
                """, id);
        assertThat(inventory.decrement(id, 1)).isFalse();
        assertThat(available(id)).isEqualTo(10);
    }

    private boolean result(java.util.concurrent.Future<Boolean> future) {
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
