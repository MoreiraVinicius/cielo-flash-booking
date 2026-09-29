package com.cielo.flashbooking.reservation.confirm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "confirmation.consumer")
public record ConfirmationConsumerProperties(boolean enabled, String queueUrl) {}
