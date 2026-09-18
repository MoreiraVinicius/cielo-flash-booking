package com.cielo.flashbooking.notification.email;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryStore {

    Optional<NotificationDelivery> claim(UUID outboxEventId, int maximumAttempts, Duration leaseDuration);

    NotificationDeliveryStatus findStatus(UUID outboxEventId);

    void markSent(UUID outboxEventId, String providerMessageId);

    void markFailed(UUID outboxEventId);

    void releaseForRetry(UUID outboxEventId);

    int deleteTerminal(int limit, Duration retention);
}
