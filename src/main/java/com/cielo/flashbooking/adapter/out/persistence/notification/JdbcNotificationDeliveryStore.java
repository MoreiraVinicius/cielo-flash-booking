package com.cielo.flashbooking.adapter.out.persistence.notification;

import com.cielo.flashbooking.notification.email.NotificationDelivery;
import com.cielo.flashbooking.notification.email.NotificationDeliveryStatus;
import com.cielo.flashbooking.notification.email.NotificationDeliveryStore;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationDeliveryStore implements NotificationDeliveryStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationDeliveryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public NotificationDelivery lockOrCreate(UUID outboxEventId) {
        jdbcTemplate.update("""
                INSERT INTO notification_delivery (id, outbox_event_id, channel, status, attempts, updated_at)
                VALUES (?, ?, 'EMAIL', 'PENDING', 0, clock_timestamp())
                ON CONFLICT (outbox_event_id) DO NOTHING
                """, UUID.randomUUID(), outboxEventId);
        return jdbcTemplate.query("""
                SELECT outbox_event_id, status, attempts
                FROM notification_delivery
                WHERE outbox_event_id = ?
                FOR UPDATE
                """, resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalStateException("notification delivery was not created");
            }
            return new NotificationDelivery(
                    resultSet.getObject("outbox_event_id", UUID.class),
                    NotificationDeliveryStatus.valueOf(resultSet.getString("status")),
                    resultSet.getInt("attempts"));
        }, outboxEventId);
    }

    @Override
    public int recordAttempt(UUID outboxEventId) {
        Integer attempts = jdbcTemplate.queryForObject("""
                UPDATE notification_delivery
                SET attempts = attempts + 1, updated_at = clock_timestamp()
                WHERE outbox_event_id = ? AND status = 'PENDING'
                RETURNING attempts
                """, Integer.class, outboxEventId);
        if (attempts == null) {
            throw new IllegalStateException("pending notification delivery was not found");
        }
        return attempts;
    }

    @Override
    public void markSent(UUID outboxEventId, String providerMessageId) {
        jdbcTemplate.update("""
                UPDATE notification_delivery
                SET status = 'SENT', provider_message_id = ?, updated_at = clock_timestamp()
                WHERE outbox_event_id = ? AND status = 'PENDING'
                """, providerMessageId, outboxEventId);
    }

    @Override
    public void markFailed(UUID outboxEventId) {
        jdbcTemplate.update("""
                UPDATE notification_delivery
                SET status = 'FAILED', updated_at = clock_timestamp()
                WHERE outbox_event_id = ? AND status = 'PENDING'
                """, outboxEventId);
    }
}
