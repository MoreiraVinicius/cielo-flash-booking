package com.cielo.flashbooking.adapter.out.persistence.outbox;

import com.cielo.flashbooking.application.outbox.OutboxEvent;
import com.cielo.flashbooking.application.outbox.OutboxEventStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxEventStore implements OutboxEventStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return jdbcTemplate.query("""
                SELECT id, event_type, payload::text, occurred_at
                FROM outbox_event
                WHERE published_at IS NULL
                ORDER BY occurred_at, id
                LIMIT ?
                """, (resultSet, rowNum) -> new OutboxEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getString("payload"),
                resultSet.getTimestamp("occurred_at").toInstant()), limit);
    }

    @Override
    public void recordAttempt(UUID eventId) {
        jdbcTemplate.update("""
                UPDATE outbox_event
                SET attempts = attempts + 1
                WHERE id = ? AND published_at IS NULL
                """, eventId);
    }

    @Override
    public void markPublished(UUID eventId, java.time.Instant publishedAt) {
        jdbcTemplate.update("""
                UPDATE outbox_event
                SET published_at = ?
                WHERE id = ? AND published_at IS NULL
                """, Timestamp.from(publishedAt), eventId);
    }

    @Override
    public int deletePublished(int limit, Duration retention) {
        return jdbcTemplate.update("""
                WITH published AS (
                    SELECT id
                    FROM outbox_event
                    WHERE published_at < clock_timestamp() - make_interval(secs => ?)
                      AND NOT EXISTS (
                          SELECT 1 FROM notification_delivery
                          WHERE notification_delivery.outbox_event_id = outbox_event.id)
                    ORDER BY published_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM outbox_event current_event
                USING published
                WHERE current_event.id = published.id
                """, Math.toIntExact(retention.toSeconds()), limit);
    }
}
