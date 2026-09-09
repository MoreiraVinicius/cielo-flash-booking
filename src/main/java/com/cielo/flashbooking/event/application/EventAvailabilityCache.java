package com.cielo.flashbooking.event.application;

import com.cielo.flashbooking.domain.event.Event;
import java.util.Optional;
import java.util.UUID;

public interface EventAvailabilityCache {

    Optional<Event> findById(UUID id);

    void put(Event event);

    void evict(UUID id);
}
