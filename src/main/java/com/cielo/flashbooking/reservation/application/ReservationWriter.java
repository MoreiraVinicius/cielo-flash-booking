package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.domain.reservation.Customer;
import com.cielo.flashbooking.domain.reservation.Reservation;
import java.util.UUID;
import java.time.Instant;
import java.util.Optional;

public interface ReservationWriter {

    boolean eventExists(UUID eventId);

    Customer upsertCustomer(Customer customer);

    void save(Reservation reservation);

    void addReservationCreatedOutboxEvent(Reservation reservation);

    Optional<CapacityRelease> cancelPending(UUID reservationId, Instant changedAt);

    record CapacityRelease(UUID eventId, int quantity) {
    }
}
