package com.cielo.flashbooking.feature.reservation.expire;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expiration.consumer")
public record ExpirationConsumerProperties(boolean enabled, String queueUrl) {
}
