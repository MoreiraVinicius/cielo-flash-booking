package com.cielo.flashbooking.event.application;

import com.cielo.flashbooking.domain.event.Event;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CreateEventService {

    private final EventWriter eventWriter;

    public CreateEventService(EventWriter eventWriter) {
        this.eventWriter = eventWriter;
    }

    public Event create(String name, int capacity) {
        return create(name, capacity, null, null);
    }

    public Event create(String name, int capacity, Instant startsAt, Instant endsAt) {
        Event event = Event.create(UUID.randomUUID(), name, capacity, eventWriter.currentTime(), startsAt, endsAt);
        return eventWriter.save(event);
    }
}
