package com.cielo.flashbooking.notification.email;

import java.util.Objects;
import java.util.UUID;

public record NotificationDelivery(UUID outboxEventId, NotificationDeliveryStatus status, int attempts) {

    public NotificationDelivery {
        Objects.requireNonNull(outboxEventId, "outboxEventId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
    }
}
