package com.cielo.flashbooking.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "notification.consumer")
public record NotificationConsumerProperties(
        boolean enabled,
        String queueUrl,
        Integer maximumAttempts,
        Duration leaseDuration) {

    public NotificationConsumerProperties {
        maximumAttempts = maximumAttempts == null ? 3 : maximumAttempts;
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(2) : leaseDuration;
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("notification.consumer.maximum-attempts must be positive");
        }
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("notification.consumer.lease-duration must be positive");
        }
    }
}
