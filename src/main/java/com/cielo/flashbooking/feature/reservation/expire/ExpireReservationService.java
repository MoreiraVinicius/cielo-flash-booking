package com.cielo.flashbooking.feature.reservation.expire;

import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import com.cielo.flashbooking.reservation.application.ReservationChanged;
import com.cielo.flashbooking.reservation.application.ReservationWriter;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpireReservationService {

    private final ReservationWriter reservationWriter;
    private final InventoryOperations inventoryOperations;
    private final ApplicationEventPublisher eventPublisher;

    public ExpireReservationService(
            ReservationWriter reservationWriter,
            InventoryOperations inventoryOperations,
            ApplicationEventPublisher eventPublisher) {
        this.reservationWriter = reservationWriter;
        this.inventoryOperations = inventoryOperations;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public boolean expire(UUID reservationId) {
        return reservationWriter.expirePending(reservationId)
                .map(release -> expireAndReturn(reservationId, release))
                .orElse(false);
    }

    private boolean expireAndReturn(UUID reservationId, ReservationWriter.CapacityRelease release) {
        if (!inventoryOperations.increment(release.eventId(), release.quantity())) {
            throw new IllegalStateException("could not return expired reservation capacity");
        }
        eventPublisher.publishEvent(new EventAvailabilityChanged(release.eventId()));
        eventPublisher.publishEvent(new ReservationChanged(reservationId));
        return true;
    }
}
