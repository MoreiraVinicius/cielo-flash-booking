package com.cielo.flashbooking.event.application;

import java.util.Objects;
import java.util.UUID;

public record EventAvailabilityChanged(UUID eventId) {

    public EventAvailabilityChanged {
        Objects.requireNonNull(eventId, "eventId must not be null");
    }
}
