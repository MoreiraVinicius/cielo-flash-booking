package com.cielo.flashbooking.event.summary;

import java.util.Optional;
import java.util.UUID;

public interface EventSummaryFactsReader {

    Optional<EventSummaryFacts> read(UUID eventId);
}
