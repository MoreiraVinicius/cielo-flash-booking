package com.cielo.flashbooking.event.summary;

public interface DiscordSummaryPublisher {

    DeliveryStatus publish(String webhookSecretArn, String markdown);

    enum DeliveryStatus {
        SENT,
        FAILED,
        UNKNOWN
    }
}
