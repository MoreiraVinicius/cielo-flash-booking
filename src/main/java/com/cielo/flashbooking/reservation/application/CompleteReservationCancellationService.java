package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteReservationCancellationService {

    private final ReservationWriter reservationWriter;
    private final InventoryOperations inventoryOperations;
    private final ApplicationEventPublisher eventPublisher;

    public CompleteReservationCancellationService(
            ReservationWriter reservationWriter,
            InventoryOperations inventoryOperations,
            ApplicationEventPublisher eventPublisher) {
        this.reservationWriter = reservationWriter;
        this.inventoryOperations = inventoryOperations;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Optional<ReservationCancellationCompletionResult> complete(UUID reservationId, UUID cancellationId) {
        return reservationWriter
                .completeConfirmedCancellation(reservationId, cancellationId)
                .map(this::releaseCapacityAndResult);
    }

    private ReservationCancellationCompletionResult releaseCapacityAndResult(
            ReservationWriter.CancellationCompletionTransition transition) {
        ReservationWriter.CapacityRelease release = transition.capacityRelease();
        if (release != null) {
            if (!inventoryOperations.increment(release.eventId(), release.quantity())) {
                throw new IllegalStateException("could not return cancelled reservation capacity");
            }
            eventPublisher.publishEvent(new EventAvailabilityChanged(release.eventId()));
        }
        return new ReservationCancellationCompletionResult(
                transition.status(), transition.cancellationId(), transition.changed());
    }
}
