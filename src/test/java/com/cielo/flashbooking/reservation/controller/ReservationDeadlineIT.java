package com.cielo.flashbooking.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import com.cielo.flashbooking.event.application.EventAvailabilityCache;
import com.cielo.flashbooking.reservation.application.CancelReservationService;
import com.cielo.flashbooking.reservation.application.ConfirmReservationService;
import com.cielo.flashbooking.reservation.application.ReservationConfirmationResult;
import com.cielo.flashbooking.reservation.expire.ExpireReservationService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("command-api")
class ReservationDeadlineIT {

    private static final String EXTERNAL_URL = System.getProperty("test.postgres.url");
    private static final String EXTERNAL_USERNAME = System.getProperty("test.postgres.username", "postgres");
    private static final String EXTERNAL_PASSWORD = System.getProperty("test.postgres.password", "postgres");
    private static final PostgreSQLContainer<?> POSTGRESQL =
            EXTERNAL_URL == null ? new PostgreSQLContainer<>("postgres:16-alpine") : null;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CancelReservationService cancelReservationService;

    @Autowired
    private ConfirmReservationService confirmReservationService;

    @Autowired
    private ExpireReservationService expireReservationService;

    @MockitoBean
    private EventAvailabilityCache eventAvailabilityCache;

    private ExecutorService executor;

    @AfterAll
    static void stopDatabase() {
        if (POSTGRESQL != null) {
            POSTGRESQL.stop();
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (POSTGRESQL != null) {
            POSTGRESQL.start();
        }
        registry.add("spring.datasource.url", ReservationDeadlineIT::jdbcUrl);
        registry.add("spring.datasource.username", ReservationDeadlineIT::username);
        registry.add("spring.datasource.password", ReservationDeadlineIT::password);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @BeforeEach
    void clearState() {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void cancel_whenBeforeDeadline_returnsCancelledAndReleasesCapacityOnce() throws Exception {
        Fixture fixture = insertPendingReservation(databaseNow().plus(Duration.ofMinutes(10)));

        mockMvc.perform(delete("/reservations/{id}", fixture.reservationId())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.closureReason.code").value("CANCELLED_BY_REQUEST"))
                .andExpect(jsonPath("$.closureReason.description").value("Reserva cancelada por solicitação"));

        assertTerminalState(fixture, "CANCELLED", "CANCELLED_BY_REQUEST", "Reserva cancelada por solicitação");
    }

    @Test
    void cancel_whenDeadlineHasPassed_returnsExpiredAndReleasesCapacityOnce() throws Exception {
        Fixture fixture = insertPendingReservation(databaseNow().minusMillis(1));
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(delete("/reservations/{id}", fixture.reservationId()).header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.closureReason.code").value("RESERVATION_DEADLINE_REACHED"))
                .andExpect(jsonPath("$.closureReason.description").value("Prazo da reserva encerrado"));

        mockMvc.perform(delete("/reservations/{id}", fixture.reservationId()).header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mockMvc.perform(delete("/reservations/{id}", fixture.reservationId())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        assertThat(expireReservationService.expire(fixture.reservationId())).isFalse();
        verify(eventAvailabilityCache).evict(fixture.eventId());
        assertTerminalState(fixture, "EXPIRED", "RESERVATION_DEADLINE_REACHED", "Prazo da reserva encerrado");
    }

    @Test
    void cancel_whenLockWaitCrossesDeadline_usesDatabaseTimeAfterTheLock() throws Exception {
        Instant expiresAt = databaseNow().plus(Duration.ofSeconds(2));
        Fixture fixture = insertPendingReservation(expiresAt);

        try (Connection blocker = connection()) {
            blocker.setAutoCommit(false);
            lockReservation(blocker, fixture.reservationId());

            Future<?> cancellation = executor.submit(() -> cancelReservationService.cancel(fixture.reservationId()));
            awaitBlockedReservationUpdate();
            awaitDatabaseDeadline(expiresAt);
            blocker.commit();
            cancellation.get(5, TimeUnit.SECONDS);
        }

        assertTerminalState(fixture, "EXPIRED", "RESERVATION_DEADLINE_REACHED", "Prazo da reserva encerrado");
    }

    @Test
    void close_whenDeleteAndExpirationRace_releasesCapacityOnceAndKeepsExpiredReason() throws Exception {
        Fixture fixture = insertPendingReservation(databaseNow().minusMillis(1));
        CountDownLatch start = new CountDownLatch(1);

        Future<?> cancellation = executor.submit(() -> {
            await(start);
            return cancelReservationService.cancel(fixture.reservationId());
        });
        Future<Boolean> expiration = executor.submit(() -> {
            await(start);
            return expireReservationService.expire(fixture.reservationId());
        });

        start.countDown();
        cancellation.get(5, TimeUnit.SECONDS);
        expiration.get(5, TimeUnit.SECONDS);

        assertTerminalState(fixture, "EXPIRED", "RESERVATION_DEADLINE_REACHED", "Prazo da reserva encerrado");
    }

    @Test
    void confirm_whenBeforeDeadlineCommitsReservationWithoutChangingCapacity() {
        Fixture fixture = insertPendingReservation(databaseNow().plus(Duration.ofMinutes(10)));
        var result = confirmReservationService.confirm(fixture.reservationId()).orElseThrow();

        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.confirmedAt()).isNotNull().isBefore(databaseNow());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM reservation WHERE id = ?", String.class, fixture.reservationId()))
                .isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT confirmed_at IS NOT NULL FROM reservation WHERE id = ?",
                        Boolean.class,
                        fixture.reservationId()))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT available FROM event WHERE id = ?", Integer.class, fixture.eventId()))
                .isEqualTo(7);
        assertThat(expireReservationService.expire(fixture.reservationId())).isFalse();
    }

    @Test
    void confirm_whenLockWaitCrossesDeadlineExpiresAndReturnsCapacityOnce() throws Exception {
        Instant expiresAt = databaseNow().plus(Duration.ofSeconds(2));
        Fixture fixture = insertPendingReservation(expiresAt);

        try (Connection blocker = connection()) {
            blocker.setAutoCommit(false);
            lockReservation(blocker, fixture.reservationId());

            Future<java.util.Optional<ReservationConfirmationResult>> confirmation =
                    executor.submit(() -> confirmReservationService.confirm(fixture.reservationId()));
            awaitBlockedReservationUpdate();
            awaitDatabaseDeadline(expiresAt);
            blocker.commit();
            assertThat(confirmation.get(5, TimeUnit.SECONDS)).isPresent();
        }

        assertTerminalState(fixture, "EXPIRED", "RESERVATION_DEADLINE_REACHED", "Prazo da reserva encerrado");
        assertThat(expireReservationService.expire(fixture.reservationId())).isFalse();
    }

    @Test
    void confirmAndCancel_whenTheyRaceOnlyCommitOrReleaseCapacityOnce() throws Exception {
        Fixture fixture = insertPendingReservation(databaseNow().plus(Duration.ofMinutes(10)));
        CountDownLatch start = new CountDownLatch(1);
        Future<?> confirmation = executor.submit(() -> {
            await(start);
            return confirmReservationService.confirm(fixture.reservationId());
        });
        Future<?> cancellation = executor.submit(() -> {
            await(start);
            return cancelReservationService.cancel(fixture.reservationId());
        });

        start.countDown();
        confirmation.get(5, TimeUnit.SECONDS);
        cancellation.get(5, TimeUnit.SECONDS);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM reservation WHERE id = ?", String.class, fixture.reservationId());
        int expectedAvailable = "CANCELLED".equals(status) ? 10 : 7;
        assertThat(status).isIn("CANCELLED", "CANCELLATION_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT available FROM event WHERE id = ?", Integer.class, fixture.eventId()))
                .isEqualTo(expectedAvailable);
    }

    @Test
    void cancel_whenConfirmedRetainsCapacityAndStableCancellationIdWhileExpirationRuns() {
        Fixture fixture = insertPendingReservation(databaseNow().plus(Duration.ofMinutes(10)));
        assertThat(confirmReservationService.confirm(fixture.reservationId())).isPresent();

        cancelReservationService.cancel(fixture.reservationId());
        UUID cancellationId = jdbcTemplate.queryForObject(
                "SELECT cancellation_id FROM reservation WHERE id = ?", UUID.class, fixture.reservationId());
        assertThat(expireReservationService.expire(fixture.reservationId())).isFalse();
        cancelReservationService.cancel(fixture.reservationId());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM reservation WHERE id = ?", String.class, fixture.reservationId()))
                .isEqualTo("CANCELLATION_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT cancellation_id FROM reservation WHERE id = ?", UUID.class, fixture.reservationId()))
                .isEqualTo(cancellationId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT available FROM event WHERE id = ?", Integer.class, fixture.eventId()))
                .isEqualTo(7);
    }

    private Fixture insertPendingReservation(Instant expiresAt) {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = expiresAt.minus(Duration.ofMinutes(10));
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, 10, 7, ?)",
                eventId,
                "Deadline event",
                Timestamp.from(createdAt));
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId,
                "Ana",
                "ana-" + reservationId + "@example.com",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        jdbcTemplate.update(
                """
                INSERT INTO reservation (
                    id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 3, 'PENDING', ?, ?, ?)
                """,
                reservationId,
                eventId,
                customerId,
                Timestamp.from(expiresAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return new Fixture(eventId, reservationId);
    }

    private void assertTerminalState(Fixture fixture, String status, String reasonCode, String reasonDescription) {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM reservation WHERE id = ?", String.class, fixture.reservationId()))
                .isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT closure_reason_code FROM reservation WHERE id = ?",
                        String.class,
                        fixture.reservationId()))
                .isEqualTo(reasonCode);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT closure_reason_description FROM reservation WHERE id = ?",
                        String.class,
                        fixture.reservationId()))
                .isEqualTo(reasonDescription);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT available FROM event WHERE id = ?", Integer.class, fixture.eventId()))
                .isEqualTo(10);
    }

    private void lockReservation(Connection connection, UUID reservationId) throws Exception {
        try (var statement = connection.prepareStatement("SELECT id FROM reservation WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, reservationId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private void awaitBlockedReservationUpdate() {
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND wait_event_type = 'Lock'
                              AND query ILIKE '%reservation%'
                        )
                        """, Boolean.class)));
    }

    private void awaitDatabaseDeadline(Instant expiresAt) {
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> !databaseNow().isBefore(expiresAt));
    }

    private Instant databaseNow() {
        return jdbcTemplate
                .queryForObject("SELECT clock_timestamp()", Timestamp.class)
                .toInstant();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(jdbcUrl(), username(), password());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while starting terminal race", exception);
        }
    }

    private static String jdbcUrl() {
        return POSTGRESQL == null ? EXTERNAL_URL : POSTGRESQL.getJdbcUrl();
    }

    private static String username() {
        return POSTGRESQL == null ? EXTERNAL_USERNAME : POSTGRESQL.getUsername();
    }

    private static String password() {
        return POSTGRESQL == null ? EXTERNAL_PASSWORD : POSTGRESQL.getPassword();
    }

    private record Fixture(UUID eventId, UUID reservationId) {}
}
