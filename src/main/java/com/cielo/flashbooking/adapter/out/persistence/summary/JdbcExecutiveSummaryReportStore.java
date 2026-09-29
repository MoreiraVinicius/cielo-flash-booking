package com.cielo.flashbooking.adapter.out.persistence.summary;

import com.cielo.flashbooking.event.summary.ClaimedExecutiveSummary;
import com.cielo.flashbooking.event.summary.DiscordSummaryPublisher;
import com.cielo.flashbooking.event.summary.EventSummaryFacts;
import com.cielo.flashbooking.event.summary.EventSummaryFactsReader;
import com.cielo.flashbooking.event.summary.ExecutiveSummaryRenderer;
import com.cielo.flashbooking.event.summary.ExecutiveSummaryReportStore;
import com.cielo.flashbooking.event.summary.OperationalSignalResult;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JdbcExecutiveSummaryReportStore implements ExecutiveSummaryReportStore {

    private final JdbcTemplate jdbcTemplate;
    private final EventSummaryFactsReader factsReader;
    private final ExecutiveSummaryRenderer renderer;
    private final TransactionTemplate transactionTemplate;

    JdbcExecutiveSummaryReportStore(
            JdbcTemplate jdbcTemplate,
            EventSummaryFactsReader factsReader,
            ExecutiveSummaryRenderer renderer,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.factsReader = factsReader;
        this.renderer = renderer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<UUID> findDueEventIds(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return jdbcTemplate.query("""
                SELECT e.id
                FROM event e
                CROSS JOIN executive_summary_control c
                WHERE c.id = TRUE
                  AND c.enabled
                  AND COALESCE(e.starts_at, e.created_at) >= c.enabled_at
                  AND e.ends_at IS NOT NULL
                  AND e.ends_at <= clock_timestamp()
                  AND NOT EXISTS (
                      SELECT 1 FROM event_executive_summary s WHERE s.event_id = e.id
                  )
                ORDER BY e.ends_at, e.id
                LIMIT ?
                """, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class), limit);
    }

    @Override
    public Optional<ClaimedExecutiveSummary> claim(UUID eventId) {
        return transactionTemplate.execute(status -> claimInTransaction(eventId));
    }

    private Optional<ClaimedExecutiveSummary> claimInTransaction(UUID eventId) {
        List<ActivationWindow> windows = jdbcTemplate.query(
                "SELECT enabled, enabled_at FROM executive_summary_control WHERE id = TRUE FOR SHARE",
                (resultSet, rowNumber) -> new ActivationWindow(
                        resultSet.getBoolean("enabled"), timestampOrNull(resultSet.getTimestamp("enabled_at"))));
        if (windows.isEmpty()
                || !windows.getFirst().enabled()
                || windows.getFirst().enabledAt() == null) {
            return Optional.empty();
        }

        List<EventWindow> eventWindows = jdbcTemplate.query(
                """
                SELECT COALESCE(starts_at, created_at) AS sale_starts_at,
                       ends_at,
                       ends_at <= clock_timestamp() AS closed
                FROM event
                WHERE id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new EventWindow(
                        resultSet.getTimestamp("sale_starts_at").toInstant(),
                        timestampOrNull(resultSet.getTimestamp("ends_at")),
                        resultSet.getBoolean("closed")),
                eventId);
        if (eventWindows.isEmpty()) {
            return Optional.empty();
        }
        EventWindow window = eventWindows.getFirst();
        if (window.endsAt() == null
                || !window.closed()
                || window.saleStartsAt().isBefore(windows.getFirst().enabledAt())) {
            return Optional.empty();
        }

        Optional<EventSummaryFacts> facts = factsReader.read(eventId);
        if (facts.isEmpty()) {
            return Optional.empty();
        }
        EventSummaryFacts snapshot = facts.get();
        String markdown = renderer.render(snapshot, OperationalSignalResult.empty(), null);
        Timestamp generatedAt = jdbcTemplate.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO event_executive_summary (
                    event_id, status, delivery_status, as_of, generated_at, markdown,
                    accepted_reservations, accepted_tickets, valid_tickets_at_close,
                    cancelled_tickets, expired_tickets, peak_minute, peak_tickets,
                    first_five_minute_tickets, first_available_zero_at
                ) VALUES (?, 'PARTIAL', 'NOT_CONFIGURED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId,
                Timestamp.from(snapshot.asOf()),
                generatedAt,
                markdown,
                snapshot.acceptedReservations(),
                snapshot.acceptedTickets(),
                snapshot.validTicketsAtClose(),
                snapshot.cancelledTicketsAtClose(),
                snapshot.expiredTicketsAtClose(),
                timestamp(snapshot.peakMinute()),
                snapshot.peakTickets(),
                snapshot.firstFiveMinuteTickets(),
                timestamp(snapshot.firstAvailableZeroAt()));
        return inserted == 1
                ? Optional.of(new ClaimedExecutiveSummary(snapshot, markdown, generatedAt.toInstant()))
                : Optional.empty();
    }

    @Override
    public void complete(
            ClaimedExecutiveSummary claim,
            String markdown,
            boolean ready,
            String modelId,
            Integer inputTokens,
            Integer outputTokens,
            String errorCode) {
        int updated = jdbcTemplate.update(
                """
                UPDATE event_executive_summary
                SET status = ?, markdown = ?, model_id = ?, input_tokens = ?, output_tokens = ?, error_code = ?
                WHERE event_id = ? AND status = 'PARTIAL'
                """,
                ready ? "READY" : "PARTIAL",
                markdown,
                modelId,
                inputTokens,
                outputTokens,
                errorCode,
                claim.facts().eventId());
        if (updated != 1) {
            throw new IllegalStateException("Executive summary claim could not be completed");
        }
    }

    @Override
    public void markDeliveryNotConfigured(UUID eventId) {
        jdbcTemplate.update(
                "UPDATE event_executive_summary SET delivery_status = 'NOT_CONFIGURED' WHERE event_id = ? AND delivery_status = 'NOT_CONFIGURED'",
                eventId);
    }

    @Override
    public Optional<String> beginDelivery(UUID eventId) {
        return jdbcTemplate.query(
                """
                UPDATE event_executive_summary
                SET delivery_status = 'UNKNOWN'
                WHERE event_id = ? AND delivery_status = 'NOT_CONFIGURED'
                RETURNING markdown
                """,
                resultSet -> resultSet.next() ? Optional.of(resultSet.getString("markdown")) : Optional.empty(),
                eventId);
    }

    @Override
    public void finishDelivery(UUID eventId, DiscordSummaryPublisher.DeliveryStatus status) {
        int updated = jdbcTemplate.update("""
                UPDATE event_executive_summary
                SET delivery_status = ?,
                    delivery_confirmed_at = CASE WHEN ? = 'SENT' THEN clock_timestamp() ELSE NULL END
                WHERE event_id = ? AND delivery_status = 'UNKNOWN'
                """, status.name(), status.name(), eventId);
        if (updated != 1) {
            throw new IllegalStateException("Executive summary delivery attempt could not be completed");
        }
    }

    private Timestamp timestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private java.time.Instant timestampOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record ActivationWindow(boolean enabled, java.time.Instant enabledAt) {}

    private record EventWindow(java.time.Instant saleStartsAt, java.time.Instant endsAt, boolean closed) {}
}
