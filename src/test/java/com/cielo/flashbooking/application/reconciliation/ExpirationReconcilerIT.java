package com.cielo.flashbooking.application.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.feature.reservation.expire.ExpireReservationService;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ExpirationReconcilerIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private ExpirationReconciler expirationReconciler;

    @Autowired
    private ExpireReservationService expireReservationService;

    @Autowired
    private ReservationReader reservationReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
    }

    @Test
    void reconcile_whenNoExpirationMessageExists_expiresTheDueReservationWithinFiveSeconds() {
        UUID eventId = insertEvent(10, 7);
        Instant expiresAt = databaseNow().minusMillis(10);
        UUID reservationId = insertPendingReservation(eventId, 3, expiresAt);

        expirationReconciler.reconcile();

        assertThat(databaseNow()).isBefore(expiresAt.plusSeconds(5));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId)).isEqualTo(10);
    }

    @Test
    void reconcile_whenThePostgresqlClockIsBeforeExpiry_doesNotSelectOrExpireTheReservation() {
        UUID eventId = insertEvent(10, 7);
        UUID reservationId = insertPendingReservation(eventId, 3, databaseNow().plusSeconds(60));

        expirationReconciler.reconcile();

        assertThat(reservationReader.findExpiredPendingIds(100)).doesNotContain(reservationId);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId)).isEqualTo(7);
    }

    @Test
    void reconcile_whenTwoWorkersFindTheSameReservation_returnsCapacityOnlyOnce() throws Exception {
        UUID eventId = insertEvent(10, 7);
        UUID reservationId = insertPendingReservation(eventId, 3, databaseNow().minusMillis(10));
        ExpirationReconciler secondWorker = new ExpirationReconciler(
                reservationReader,
                expireReservationService,
                new ExpirationReconciliationProperties(null));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> workers = List.of(
                    () -> {
                        expirationReconciler.reconcile();
                        return null;
                    },
                    () -> {
                        secondWorker.reconcile();
                        return null;
                    });

            for (var result : executor.invokeAll(workers)) {
                result.get();
            }

            assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                    .isEqualTo("EXPIRED");
            assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId)).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID insertEvent(int capacity, int available) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                eventId,
                "Reconciliation event",
                capacity,
                available,
                java.sql.Timestamp.from(Instant.now()));
        return eventId;
    }

    private Instant databaseNow() {
        return jdbcTemplate.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class).toInstant();
    }

    private UUID insertPendingReservation(UUID eventId, int quantity, Instant expiresAt) {
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = expiresAt.minusSeconds(600);
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId,
                "Reconciliation customer",
                customerId + "@example.com",
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """,
                reservationId,
                eventId,
                customerId,
                quantity,
                java.sql.Timestamp.from(expiresAt),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        return reservationId;
    }
}
