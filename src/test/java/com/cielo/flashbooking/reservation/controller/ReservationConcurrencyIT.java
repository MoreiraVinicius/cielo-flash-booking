package com.cielo.flashbooking.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.controller.error.ResourceConflictException;
import com.cielo.flashbooking.reservation.application.CreateReservationService;
import com.cielo.flashbooking.reservation.application.CancelReservationService;
import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ReservationConcurrencyIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private CreateReservationService createReservationService;

    @Autowired
    private CancelReservationService cancelReservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void clearState() {
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
        executor = Executors.newFixedThreadPool(20);
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void create_whenConcurrentRequestsExceedCapacity_neverOversellsAndWritesOnlyAcceptedReservations() throws Exception {
        UUID eventId = insertEvent(10);
        List<Callable<Boolean>> requests = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            int requestNumber = index;
            requests.add(() -> reserve(eventId, requestNumber));
        }

        List<Future<Boolean>> results = executor.invokeAll(requests);
        long accepted = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                accepted++;
            }
        }

        assertThat(accepted).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation WHERE event_id = ?", Integer.class, eventId))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isEqualTo(10);
    }

    @Test
    void cancel_whenConcurrentRequestsTargetTheSameReservation_returnsCapacityOnlyOnce() throws Exception {
        UUID eventId = insertEvent(10);
        UUID reservationId = insertPendingReservation(eventId, 3);
        List<Callable<Boolean>> requests = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            requests.add(() -> {
                cancelReservationService.cancel(reservationId);
                return true;
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(requests);
        for (Future<Boolean> result : results) {
            assertThat(result.get()).isTrue();
        }

        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("CANCELLED");
    }

    private boolean reserve(UUID eventId, int requestNumber) {
        try {
            createReservationService.create(eventId, 1, "Customer " + requestNumber, "customer" + requestNumber + "@example.com");
            return true;
        } catch (ResourceConflictException exception) {
            return false;
        }
    }

    private UUID insertEvent(int capacity) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                id,
                "Concurrent reservation event",
                capacity,
                capacity,
                java.sql.Timestamp.from(Instant.parse("2026-09-09T12:00:00Z")));
        return id;
    }

    private UUID insertPendingReservation(UUID eventId, int quantity) {
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-09T12:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId,
                "Cancel customer",
                "cancel@example.com",
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update(
                "INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)",
                reservationId,
                eventId,
                customerId,
                quantity,
                java.sql.Timestamp.from(createdAt.plusSeconds(600)),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update("UPDATE event SET available = available - ? WHERE id = ?", quantity, eventId);
        return reservationId;
    }
}
