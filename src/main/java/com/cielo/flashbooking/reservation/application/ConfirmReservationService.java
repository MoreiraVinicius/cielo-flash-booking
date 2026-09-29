package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmReservationService {

    private final ReservationWriter reservationWriter;
    private final InventoryOperations inventoryOperations;
    private final ApplicationEventPublisher eventPublisher;

    public ConfirmReservationService(
            ReservationWriter reservationWriter,
            InventoryOperations inventoryOperations,
            ApplicationEventPublisher eventPublisher) {
        this.reservationWriter = reservationWriter;
        this.inventoryOperations = inventoryOperations;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Optional<ReservationConfirmationResult> confirm(UUID reservationId) {
        return reservationWriter.confirmPending(reservationId).map(this::releaseExpiredCapacityAndResult);
    }

    private ReservationConfirmationResult releaseExpiredCapacityAndResult(
            ReservationWriter.ConfirmationTransition transition) {
        ReservationWriter.CapacityRelease release = transition.expiredCapacityRelease();
        if (release != null) {
            if (!inventoryOperations.increment(release.eventId(), release.quantity())) {
                throw new IllegalStateException("could not return expired reservation capacity");
            }
            eventPublisher.publishEvent(new EventAvailabilityChanged(release.eventId()));
        }
        return new ReservationConfirmationResult(transition.status(), transition.confirmedAt());
    }
}
