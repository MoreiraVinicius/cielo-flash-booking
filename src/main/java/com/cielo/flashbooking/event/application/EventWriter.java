package com.cielo.flashbooking.event.application;

import com.cielo.flashbooking.domain.event.Event;

public interface EventWriter {

    Event save(Event event);
}
