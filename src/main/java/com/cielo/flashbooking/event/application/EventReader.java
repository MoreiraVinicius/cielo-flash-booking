package com.cielo.flashbooking.event.application;

import com.cielo.flashbooking.domain.event.Event;
import java.util.Optional;
import java.util.UUID;

public interface EventReader {

    Optional<Event> findById(UUID id);
}
