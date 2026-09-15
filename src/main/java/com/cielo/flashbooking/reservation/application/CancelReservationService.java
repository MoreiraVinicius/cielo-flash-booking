package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelReservationService {

    private final ReservationWriter reservationWriter;
    private final ReservationReader reservationReader;
    private final InventoryOperations inventoryOperations;
    private final ApplicationEventPublisher eventPublisher;

    public CancelReservationService(
            ReservationWriter reservationWriter,
            ReservationReader reservationReader,
            InventoryOperations inventoryOperations,
            ApplicationEventPublisher eventPublisher) {
        this.reservationWriter = reservationWriter;
        this.reservationReader = reservationReader;
        this.inventoryOperations = inventoryOperations;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public ReservationDetails cancel(UUID reservationId) {
        return reservationWriter.closePendingOnCancellation(reservationId)
                .map(release -> closeAndReturn(reservationId, release))
                .orElseGet(() -> reservationReader.findById(reservationId)
                        .orElseThrow(() -> new ResourceNotFoundException("reservation not found: " + reservationId)));
    }

    private ReservationDetails closeAndReturn(UUID reservationId, ReservationWriter.CapacityRelease release) {
        if (!inventoryOperations.increment(release.eventId(), release.quantity())) {
            throw new IllegalStateException("could not return reservation capacity");
        }
        ReservationDetails reservation = reservationReader.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("closed reservation disappeared"));
        eventPublisher.publishEvent(new EventAvailabilityChanged(release.eventId()));
        return reservation;
    }
}
