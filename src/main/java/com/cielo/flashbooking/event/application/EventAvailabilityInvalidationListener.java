package com.cielo.flashbooking.event.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class EventAvailabilityInvalidationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventAvailabilityInvalidationListener.class);

    private final EventAvailabilityCache cache;

    EventAvailabilityInvalidationListener(EventAvailabilityCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void invalidate(EventAvailabilityChanged event) {
        try {
            cache.evict(event.eventId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not invalidate event availability cache eventId={}", event.eventId(), exception);
        }
    }
}
