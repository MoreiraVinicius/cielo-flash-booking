package com.cielo.flashbooking.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.consumer")
public record NotificationConsumerProperties(
        boolean enabled,
        String queueUrl,
        Integer maximumAttempts,
        Integer poolSize) {

    public NotificationConsumerProperties {
        maximumAttempts = maximumAttempts == null ? 3 : maximumAttempts;
        poolSize = poolSize == null ? 2 : poolSize;
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("notification.consumer.maximum-attempts must be positive");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("notification.consumer.pool-size must be positive");
        }
    }
}
