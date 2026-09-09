package com.cielo.flashbooking.notification.email;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.email")
public record NotificationEmailProperties(
        String provider,
        String fromAddress,
        String region,
        String endpoint,
        Duration apiCallTimeout) {

    public NotificationEmailProperties {
        region = region == null ? "sa-east-1" : region;
        apiCallTimeout = apiCallTimeout == null ? Duration.ofSeconds(3) : apiCallTimeout;
    }
}
