package com.cielo.flashbooking.adapter.out.persistence.summary;

import com.cielo.flashbooking.event.summary.ExecutiveSummaryReportReader;
import com.cielo.flashbooking.event.summary.ExecutiveSummaryReportSnapshot;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcExecutiveSummaryReportReader implements ExecutiveSummaryReportReader {

    private final JdbcTemplate jdbcTemplate;

    JdbcExecutiveSummaryReportReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ExecutiveSummaryReportSnapshot> findByEventId(UUID eventId) {
        return jdbcTemplate.query(
                """
                SELECT CASE
                           WHEN s.event_id IS NOT NULL THEN s.status
                           WHEN NOT c.enabled THEN 'DISABLED'
                           WHEN COALESCE(e.starts_at, e.created_at) < c.enabled_at THEN 'NOT_ELIGIBLE'
                           ELSE 'SCHEDULED'
                       END AS status,
                       s.markdown, s.generated_at, s.as_of, s.delivery_status
                FROM event e
                CROSS JOIN executive_summary_control c
                LEFT JOIN event_executive_summary s ON s.event_id = e.id
                WHERE e.id = ? AND c.id = TRUE
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(new ExecutiveSummaryReportSnapshot(
                                resultSet.getString("status"),
                                resultSet.getString("markdown"),
                                instantOrNull(resultSet.getTimestamp("generated_at")),
                                instantOrNull(resultSet.getTimestamp("as_of")),
                                resultSet.getString("delivery_status")))
                        : Optional.empty(),
                eventId);
    }

    private java.time.Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
