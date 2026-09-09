package com.cielo.flashbooking.reservation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ReservationCacheInvalidationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationCacheInvalidationListener.class);

    private final ReservationCache cache;

    ReservationCacheInvalidationListener(ReservationCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void invalidate(ReservationChanged event) {
        try {
            cache.evict(event.reservationId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not invalidate reservation cache reservationId={}", event.reservationId(), exception);
        }
    }
}
