package com.cielo.flashbooking.adapter.out.persistence.summary;

import com.cielo.flashbooking.event.summary.ExecutiveSummaryActivation;
import com.cielo.flashbooking.event.summary.ExecutiveSummaryControlStore;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcExecutiveSummaryControlStore implements ExecutiveSummaryControlStore {

    private final JdbcTemplate jdbcTemplate;

    JdbcExecutiveSummaryControlStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ExecutiveSummaryActivation setEnabled(boolean enabled) {
        return jdbcTemplate.queryForObject(
                """
                UPDATE executive_summary_control
                SET enabled = ?,
                    enabled_at = CASE WHEN ? THEN COALESCE(enabled_at, clock_timestamp()) ELSE NULL END,
                    updated_at = clock_timestamp()
                WHERE id = TRUE
                RETURNING enabled, enabled_at, updated_at
                """,
                (resultSet, rowNumber) -> new ExecutiveSummaryActivation(
                        resultSet.getBoolean("enabled"),
                        timestampOrNull(resultSet.getTimestamp("enabled_at")),
                        resultSet.getTimestamp("updated_at").toInstant()),
                enabled,
                enabled);
    }

    private java.time.Instant timestampOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
