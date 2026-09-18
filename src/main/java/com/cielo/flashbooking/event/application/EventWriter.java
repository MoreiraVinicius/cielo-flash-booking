package com.cielo.flashbooking.event.application;

import com.cielo.flashbooking.domain.event.Event;
import java.time.Instant;

public interface EventWriter {

    Instant currentTime();

    Event save(Event event);
}
