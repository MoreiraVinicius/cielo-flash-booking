package com.cielo.flashbooking.event.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "executive-summary.scheduler.fixed-delay=1h")
@ActiveProfiles("command-api")
class EventSummarySchedulerIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExecutiveSummaryReportStore reportStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ExecutiveSummaryRenderer renderer;

    private OperationalSignalsReader signals;
    private ExecutiveNarrative narrative;
    private ExecutiveSummaryDeliveryService deliveryService;
    private EventSummaryScheduler scheduler;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM event_executive_summary");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
        jdbcTemplate.update("UPDATE executive_summary_control SET enabled = FALSE, enabled_at = NULL WHERE id = TRUE");
        signals = mock(OperationalSignalsReader.class);
        narrative = mock(ExecutiveNarrative.class);
        deliveryService = mock(ExecutiveSummaryDeliveryService.class);
        when(signals.read(any(), any())).thenReturn(OperationalSignalResult.empty());
        when(narrative.write(any()))
                .thenReturn(Optional.of(new ExecutiveNarrative.Narrative(
                        "As reservas se concentraram no início.", "amazon.nova-micro-v1:0", 40, 12)));
        scheduler = new EventSummaryScheduler(
                reportStore, signals, narrative, renderer, new EventSummarySchedulerProperties(), deliveryService);
    }

    @Test
    void scanWaitsForCloseAndDoesNoWorkWhileTheGlobalFlagIsOff() {
        Instant now = databaseNow();
        UUID eventId = insertEvent(now.minusSeconds(600), now.plusSeconds(60));

        scheduler.scan();
        activateSince(databaseNow().minusSeconds(900));
        scheduler.scan();

        assertThat(reportCount(eventId)).isZero();
        verify(signals, never()).read(any(), any());
        verify(narrative, never()).write(any());
    }

    @Test
    void concurrentScansPersistOneClaimAndRunExternalAnalysisOnlyOnce() throws Exception {
        Instant now = databaseNow();
        activateSince(now.minusSeconds(3600));
        UUID eventId = insertEvent(now.minusSeconds(1800), now.minusSeconds(60));
        insertPendingReservation(eventId, now.minusSeconds(1500), now.plusSeconds(1800));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(scheduler::scan);
            var second = executor.submit(scheduler::scan);
            first.get();
            second.get();
        }
        scheduler.scan();

        assertThat(reportCount(eventId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .isEqualTo("READY");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT delivery_status FROM event_executive_summary WHERE event_id = ?",
                        String.class,
                        eventId))
                .isEqualTo("NOT_CONFIGURED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT markdown FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .contains("1 ingressos passaram por 1 reservas aceitas", "As reservas se concentraram")
                .doesNotContain("Discord:");
        verify(signals).read(any(), any());
        verify(narrative).write(any());
    }

    @Test
    void scanExcludesEventsOutsideTheCurrentActivationWindow() {
        Instant now = databaseNow();
        UUID closedWhileDisabled = insertEvent(now.minusSeconds(600), now.plusSeconds(60));
        activateSince(now.minusSeconds(1200));
        scheduler.scan();
        assertThat(reportCount(closedWhileDisabled)).isZero();

        jdbcTemplate.update("UPDATE executive_summary_control SET enabled = FALSE, enabled_at = NULL WHERE id = TRUE");
        jdbcTemplate.update(
                "UPDATE event SET ends_at = clock_timestamp() - interval '1 second' WHERE id = ?", closedWhileDisabled);
        activateSince(databaseNow());
        UUID startedBeforeCurrentWindow = insertEvent(now.minusSeconds(7200), now.minusSeconds(60));

        scheduler.scan();

        assertThat(reportCount(closedWhileDisabled)).isZero();
        assertThat(reportCount(startedBeforeCurrentWindow)).isZero();
        verify(signals, never()).read(any(), any());
        verify(narrative, never()).write(any());
    }

    @Test
    void scanWaitsForInventoryTransactionAndIncludesItsAcceptedReservation() throws Exception {
        Instant now = databaseNow();
        activateSince(now.minusSeconds(600));
        UUID eventId = insertEvent(now.minusSeconds(120), now.minusSeconds(30));
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email) VALUES (?, 'Summary Buyer', ?)",
                customerId,
                customerId + "@example.com");
        CountDownLatch inventoryUpdated = new CountDownLatch(1);
        CountDownLatch commitInventory = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var inventoryTransaction =
                    executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        jdbcTemplate.update("UPDATE event SET available = 9 WHERE id = ?", eventId);
                        Instant acceptedAt = now.minusSeconds(60);
                        jdbcTemplate.update(
                                "INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at) VALUES (?, ?, ?, 1, 'PENDING', ?, ?, ?)",
                                UUID.randomUUID(),
                                eventId,
                                customerId,
                                Timestamp.from(now.plusSeconds(3600)),
                                Timestamp.from(acceptedAt),
                                Timestamp.from(acceptedAt));
                        inventoryUpdated.countDown();
                        try {
                            commitInventory.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(exception);
                        }
                    }));
            assertThat(inventoryUpdated.await(5, TimeUnit.SECONDS)).isTrue();

            var scan = executor.submit(scheduler::scan);
            Thread.sleep(300);
            assertThat(scan.isDone()).isFalse();

            commitInventory.countDown();
            inventoryTransaction.get(5, TimeUnit.SECONDS);
            scan.get(5, TimeUnit.SECONDS);
        } finally {
            commitInventory.countDown();
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT accepted_tickets FROM event_executive_summary WHERE event_id = ?", Long.class, eventId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT markdown FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .contains("1 ingressos passaram por 1 reservas aceitas");
    }

    @Test
    void inconsistentClosePartitionIsPersistedWithoutCallingExternalAdapters() {
        Instant now = databaseNow();
        activateSince(now.minusSeconds(600));
        UUID eventId = insertEvent(now.minusSeconds(600), now.minusSeconds(60));
        UUID customerId = UUID.randomUUID();
        Instant createdAt = now.minusSeconds(300);
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email) VALUES (?, 'Summary Buyer', ?)",
                customerId,
                customerId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at, closure_reason_code, closure_reason_description) VALUES (?, ?, ?, 5, 'EXPIRED', ?, ?, ?, 'RESERVATION_DEADLINE_REACHED', 'Prazo da reserva encerrado')",
                UUID.randomUUID(),
                eventId,
                customerId,
                Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt.plusSeconds(30)));

        scheduler.scan();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .isEqualTo("PARTIAL");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT markdown FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .contains("Apuração incompleta")
                .doesNotContain("5 ingressos passaram", "## Ritmo");
        verify(signals, never()).read(any(), any());
        verify(narrative, never()).write(any());
    }

    @Test
    void persistedClaimSurvivesWorkerInterruptionBeforeExternalAnalysis() {
        Instant now = databaseNow();
        activateSince(now.minusSeconds(600));
        UUID eventId = insertEvent(now.minusSeconds(600), now.minusSeconds(60));
        insertPendingReservation(eventId, now.minusSeconds(300), now.plusSeconds(3600));

        assertThat(reportStore.claim(eventId)).isPresent();
        scheduler.scan();

        assertThat(reportCount(eventId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .isEqualTo("PARTIAL");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT markdown FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .contains("1 ingressos passaram por 1 reservas aceitas");
        verify(signals, never()).read(any(), any());
        verify(narrative, never()).write(any());
        verify(deliveryService, never()).deliver(any());
    }

    @Test
    void failedExternalAnalysisLeavesDurablePartialReportAndIsNotRetried() {
        Instant now = databaseNow();
        activateSince(now.minusSeconds(3600));
        UUID eventId = insertEvent(now.minusSeconds(1800), now.minusSeconds(60));
        when(signals.read(any(), any())).thenThrow(new IllegalStateException("cloud unavailable"));

        scheduler.scan();
        scheduler.scan();

        assertThat(reportCount(eventId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .isEqualTo("PARTIAL");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_code FROM event_executive_summary WHERE event_id = ?", String.class, eventId))
                .isEqualTo("OPERATIONAL_SIGNALS_UNAVAILABLE");
        verify(signals).read(any(), any());
        verify(narrative, never()).write(any());
    }

    private UUID insertEvent(Instant startsAt, Instant endsAt) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at, starts_at, ends_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                eventId,
                "Summary test event",
                10,
                10,
                Timestamp.from(startsAt.minusSeconds(60)),
                Timestamp.from(startsAt),
                Timestamp.from(endsAt));
        return eventId;
    }

    private void insertPendingReservation(UUID eventId, Instant createdAt, Instant expiresAt) {
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email) VALUES (?, 'Summary Buyer', ?)",
                customerId,
                customerId + "@example.com");
        jdbcTemplate.update(
                """
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'PENDING', ?, ?, ?)
                """,
                UUID.randomUUID(),
                eventId,
                customerId,
                Timestamp.from(expiresAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private void activateSince(Instant enabledAt) {
        jdbcTemplate.update(
                "UPDATE executive_summary_control SET enabled = TRUE, enabled_at = ? WHERE id = TRUE",
                Timestamp.from(enabledAt));
    }

    private Instant databaseNow() {
        return jdbcTemplate
                .queryForObject("SELECT clock_timestamp()", Timestamp.class)
                .toInstant();
    }

    private long reportCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_executive_summary WHERE event_id = ?", Long.class, eventId);
    }
}
