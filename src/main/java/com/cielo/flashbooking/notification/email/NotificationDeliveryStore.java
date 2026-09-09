package com.cielo.flashbooking.notification.email;

import java.util.UUID;

public interface NotificationDeliveryStore {

    NotificationDelivery lockOrCreate(UUID outboxEventId);

    int recordAttempt(UUID outboxEventId);

    void markSent(UUID outboxEventId, String providerMessageId);

    void markFailed(UUID outboxEventId);
}
