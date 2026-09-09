package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.application.error.ResourceConflictException;
import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import com.cielo.flashbooking.domain.reservation.Customer;
import com.cielo.flashbooking.domain.reservation.Reservation;
import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateReservationService {

    private final ReservationWriter reservationWriter;
    private final InventoryOperations inventoryOperations;
    private final ReservationProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public CreateReservationService(
            ReservationWriter reservationWriter,
            InventoryOperations inventoryOperations,
            ReservationProperties properties,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.reservationWriter = reservationWriter;
        this.inventoryOperations = inventoryOperations;
        this.properties = properties;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(noRollbackFor = {
            ResourceNotFoundException.class,
            ResourceConflictException.class,
            IllegalArgumentException.class})
    public CreatedReservation create(UUID eventId, int quantity, String customerName, String customerEmail) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Instant createdAt = clock.instant();
        Customer requestedCustomer = Customer.create(UUID.randomUUID(), customerName, customerEmail, createdAt);

        if (!reservationWriter.eventExists(eventId)) {
            throw new ResourceNotFoundException("event was not found");
        }
        if (!inventoryOperations.decrement(eventId, quantity)) {
            throw new ResourceConflictException("event does not have enough available capacity");
        }

        Customer customer = reservationWriter.upsertCustomer(requestedCustomer);
        Reservation reservation = Reservation.pending(
                UUID.randomUUID(),
                eventId,
                customer,
                quantity,
                createdAt.plus(properties.holdDuration()),
                createdAt);
        reservationWriter.save(reservation);
        reservationWriter.addReservationCreatedOutboxEvent(reservation);
        reservationWriter.addReservationExpirationScheduledOutboxEvent(reservation);
        eventPublisher.publishEvent(new EventAvailabilityChanged(eventId));
        return new CreatedReservation(reservation, customer);
    }
}
