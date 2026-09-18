package com.cielo.flashbooking.event.controller;

import com.cielo.flashbooking.domain.event.Event;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id, String name, int capacity, int available, Instant createdAt, Instant startsAt, Instant endsAt) {

    static EventResponse from(Event event) {
        return new EventResponse(
                event.id(),
                event.name(),
                event.capacity(),
                event.available(),
                event.createdAt(),
                event.startsAt(),
                event.endsAt());
    }
}
