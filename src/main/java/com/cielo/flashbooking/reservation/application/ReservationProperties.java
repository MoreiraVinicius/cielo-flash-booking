package com.cielo.flashbooking.reservation.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reservation")
public record ReservationProperties(Duration holdDuration) {

    public ReservationProperties {
        if (holdDuration == null || holdDuration.isZero() || holdDuration.isNegative()) {
            throw new IllegalArgumentException("reservation.hold-duration must be positive");
        }
    }
}
