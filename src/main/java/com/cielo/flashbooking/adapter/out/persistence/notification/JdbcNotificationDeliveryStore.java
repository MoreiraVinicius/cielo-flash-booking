package com.cielo.flashbooking.adapter.out.persistence.notification;

import com.cielo.flashbooking.notification.email.NotificationDelivery;
import com.cielo.flashbooking.notification.email.NotificationDeliveryStatus;
import com.cielo.flashbooking.notification.email.NotificationDeliveryStore;
import java.util.UUID;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationDeliveryStore implements NotificationDeliveryStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationDeliveryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<NotificationDelivery> claim(
            UUID outboxEventId, int maximumAttempts, Duration leaseDuration) {
        return jdbcTemplate.query("""
                INSERT INTO notification_delivery (
                    id, outbox_event_id, channel, status, attempts, lease_until, updated_at)
                VALUES (?, ?, 'EMAIL', 'SENDING', 1,
                        clock_timestamp() + make_interval(secs => ?), clock_timestamp())
                ON CONFLICT (outbox_event_id) DO UPDATE
                SET status = 'SENDING',
                    attempts = notification_delivery.attempts + 1,
                    lease_until = clock_timestamp() + make_interval(secs => ?),
                    updated_at = clock_timestamp()
                WHERE notification_delivery.attempts < ?
                  AND (notification_delivery.status = 'PENDING'
                       OR (notification_delivery.status = 'SENDING'
                           AND notification_delivery.lease_until <= clock_timestamp()))
                RETURNING outbox_event_id, status, attempts
                """, resultSet -> resultSet.next() ? Optional.of(new NotificationDelivery(
                    resultSet.getObject("outbox_event_id", UUID.class),
                    NotificationDeliveryStatus.valueOf(resultSet.getString("status")),
                    resultSet.getInt("attempts"))) : Optional.empty(),
                UUID.randomUUID(),
                outboxEventId,
                Math.toIntExact(leaseDuration.toSeconds()),
                Math.toIntExact(leaseDuration.toSeconds()),
                maximumAttempts);
    }

    @Override
    public NotificationDeliveryStatus findStatus(UUID outboxEventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM notification_delivery WHERE outbox_event_id = ?",
                (resultSet, rowNum) -> NotificationDeliveryStatus.valueOf(resultSet.getString("status")),
                outboxEventId);
    }

    @Override
    public void markSent(UUID outboxEventId, String providerMessageId) {
        jdbcTemplate.update("""
                UPDATE notification_delivery
                SET status = 'SENT', provider_message_id = ?, lease_until = NULL, updated_at = clock_timestamp()
                WHERE outbox_event_id = ? AND status = 'SENDING'
                """, providerMessageId, outboxEventId);
    }

    @Override
    public void markFailed(UUID outboxEventId) {
        jdbcTemplate.update("""
                UPDATE notification_delivery
                SET status = 'FAILED', lease_until = NULL, updated_at = clock_timestamp()
                WHERE outbox_event_id = ? AND status = 'SENDING'
                """, outboxEventId);
    }

    @Override
    public void releaseForRetry(UUID outboxEventId) {
        jdbcTemplate.update("""
                UPDATE notification_delivery
                SET status = 'PENDING', lease_until = NULL, updated_at = clock_timestamp()
                WHERE outbox_event_id = ? AND status = 'SENDING'
                """, outboxEventId);
    }

    @Override
    public int deleteTerminal(int limit, Duration retention) {
        return jdbcTemplate.update("""
                WITH terminal AS (
                    SELECT id
                    FROM notification_delivery
                    WHERE status IN ('SENT', 'FAILED')
                      AND updated_at < clock_timestamp() - make_interval(secs => ?)
                    ORDER BY updated_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM notification_delivery current_delivery
                USING terminal
                WHERE current_delivery.id = terminal.id
                """, Math.toIntExact(retention.toSeconds()), limit);
    }
}
