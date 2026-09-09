package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelReservationService {

    private final ReservationWriter reservationWriter;
    private final ReservationReader reservationReader;
    private final InventoryOperations inventoryOperations;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public CancelReservationService(
            ReservationWriter reservationWriter,
            ReservationReader reservationReader,
            InventoryOperations inventoryOperations,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.reservationWriter = reservationWriter;
        this.reservationReader = reservationReader;
        this.inventoryOperations = inventoryOperations;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ReservationDetails cancel(UUID reservationId) {
        return reservationWriter.cancelPending(reservationId, clock.instant())
                .map(release -> cancelAndReturn(reservationId, release))
                .orElseGet(() -> reservationReader.findById(reservationId)
                        .orElseThrow(() -> new ResourceNotFoundException("reservation not found: " + reservationId)));
    }

    private ReservationDetails cancelAndReturn(UUID reservationId, ReservationWriter.CapacityRelease release) {
        if (!inventoryOperations.increment(release.eventId(), release.quantity())) {
            throw new IllegalStateException("could not return reservation capacity");
        }
        ReservationDetails reservation = reservationReader.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("cancelled reservation disappeared"));
        eventPublisher.publishEvent(new EventAvailabilityChanged(release.eventId()));
        eventPublisher.publishEvent(new ReservationChanged(reservationId));
        return reservation;
    }
}
