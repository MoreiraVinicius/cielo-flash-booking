package com.cielo.flashbooking.adapter.out.messaging.publisher;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.publisher")
public record OutboxPublisherProperties(
        boolean enabled,
        String expirationQueueUrl,
        String notificationQueueUrl,
        String region,
        String endpoint,
        Duration apiCallTimeout) {

    public OutboxPublisherProperties {
        region = region == null ? "sa-east-1" : region;
        apiCallTimeout = apiCallTimeout == null ? Duration.ofSeconds(3) : apiCallTimeout;
    }
}
