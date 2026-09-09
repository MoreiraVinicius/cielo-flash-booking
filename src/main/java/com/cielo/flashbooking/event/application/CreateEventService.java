package com.cielo.flashbooking.event.application;

import com.cielo.flashbooking.domain.event.Event;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateEventService {

    private final EventWriter eventWriter;
    private final Clock clock;

    public CreateEventService(EventWriter eventWriter, Clock clock) {
        this.eventWriter = eventWriter;
        this.clock = clock;
    }

    @Transactional
    public Event create(String name, int capacity) {
        Event event = Event.create(UUID.randomUUID(), name, capacity, Instant.now(clock));
        return eventWriter.save(event);
    }
}
