package com.cielo.flashbooking.adapter.out.persistence.summary;

import com.cielo.flashbooking.event.summary.EventSummaryFacts;
import com.cielo.flashbooking.event.summary.EventSummaryFactsReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEventSummaryFactsReader implements EventSummaryFactsReader {

    private final JdbcTemplate jdbcTemplate;

    JdbcEventSummaryFactsReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<EventSummaryFacts> read(UUID eventId) {
        return jdbcTemplate.query(
                FACTS_QUERY, resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(), eventId);
    }

    private EventSummaryFacts map(ResultSet resultSet) throws SQLException {
        long acceptedTickets = resultSet.getLong("accepted_tickets");
        long validTickets = resultSet.getLong("valid_tickets_at_close");
        long cancelledTickets = resultSet.getLong("cancelled_tickets_at_close");
        long expiredTickets = resultSet.getLong("expired_tickets_at_close");
        Long peakTickets = nullableLong(resultSet, "peak_tickets");
        Long firstFiveMinuteTickets = nullableLong(resultSet, "first_five_minute_tickets");
        boolean complete = acceptedTickets == validTickets + cancelledTickets + expiredTickets
                && (peakTickets == null || peakTickets > 0)
                && (firstFiveMinuteTickets == null || firstFiveMinuteTickets <= acceptedTickets);

        return new EventSummaryFacts(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getInt("capacity"),
                resultSet.getTimestamp("sale_starts_at").toInstant(),
                resultSet.getTimestamp("ends_at").toInstant(),
                resultSet.getLong("accepted_reservations"),
                acceptedTickets,
                validTickets,
                cancelledTickets,
                expiredTickets,
                timestampOrNull(resultSet.getTimestamp("peak_minute")),
                peakTickets,
                firstFiveMinuteTickets,
                timestampOrNull(resultSet.getTimestamp("first_available_zero_at")),
                resultSet.getTimestamp("as_of").toInstant(),
                complete);
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private java.time.Instant timestampOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static final String FACTS_QUERY = """
            WITH event_window AS (
                SELECT id, name, capacity, created_at, starts_at, ends_at, first_available_zero_at,
                       COALESCE(starts_at, created_at) AS sale_starts_at
                FROM event
                WHERE id = ? AND ends_at IS NOT NULL
            ), accepted AS (
                SELECT r.id, r.quantity, r.created_at, r.expires_at, r.status, r.confirmed_at, r.updated_at,
                       w.sale_starts_at, w.ends_at
                FROM reservation r
                JOIN event_window w ON w.id = r.event_id
                WHERE r.created_at >= w.sale_starts_at AND r.created_at < w.ends_at
            ), totals AS (
                SELECT count(*)::bigint AS accepted_reservations,
                       COALESCE(sum(quantity), 0)::bigint AS accepted_tickets,
                       COALESCE(sum(quantity) FILTER (
                           WHERE (
                               status IN ('CONFIRMED', 'CANCELLATION_PENDING') AND confirmed_at <= ends_at
                           ) OR (
                               status = 'CANCELLED' AND confirmed_at IS NOT NULL
                               AND confirmed_at <= ends_at AND updated_at > ends_at
                           ) OR (
                               expires_at > ends_at AND (
                                   status = 'PENDING'
                                   OR (updated_at > ends_at AND status IN ('CANCELLED', 'EXPIRED'))
                                   OR (confirmed_at > ends_at AND status IN ('CONFIRMED', 'CANCELLATION_PENDING'))
                               )
                           )
                       ), 0)::bigint AS valid_tickets_at_close,
                       COALESCE(sum(quantity) FILTER (
                           WHERE status = 'CANCELLED' AND updated_at <= ends_at
                       ), 0)::bigint AS cancelled_tickets_at_close,
                       COALESCE(sum(quantity) FILTER (
                           WHERE expires_at <= ends_at AND status IN ('PENDING', 'EXPIRED')
                       ), 0)::bigint AS expired_tickets_at_close,
                       COALESCE(sum(quantity) FILTER (
                           WHERE created_at < sale_starts_at + interval '5 minutes'
                       ), 0)::bigint AS first_five_minute_tickets,
                       max(ends_at - sale_starts_at) AS sale_duration
                FROM accepted
            ), peak AS (
                SELECT date_trunc('minute', created_at) AS peak_minute,
                       sum(quantity)::bigint AS peak_tickets
                FROM accepted
                GROUP BY date_trunc('minute', created_at)
                ORDER BY peak_tickets DESC, peak_minute ASC
                LIMIT 1
            )
            SELECT w.id, w.name, w.capacity, w.sale_starts_at, w.ends_at, w.first_available_zero_at,
                   clock_timestamp() AS as_of,
                   t.accepted_reservations, t.accepted_tickets, t.valid_tickets_at_close,
                   t.cancelled_tickets_at_close, t.expired_tickets_at_close,
                   CASE WHEN t.sale_duration >= interval '10 minutes' AND t.accepted_tickets > 0
                        THEN t.first_five_minute_tickets ELSE NULL END AS first_five_minute_tickets,
                   p.peak_minute, p.peak_tickets
            FROM event_window w
            CROSS JOIN totals t
            LEFT JOIN peak p ON TRUE
            """;
}
