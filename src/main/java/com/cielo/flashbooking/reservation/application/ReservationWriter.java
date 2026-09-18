package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.domain.reservation.Customer;
import com.cielo.flashbooking.domain.reservation.Reservation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReservationWriter {

    Instant currentTime();

    boolean eventExists(UUID eventId);

    Customer upsertCustomer(Customer customer);

    void save(Reservation reservation);

    void addReservationCreatedOutboxEvent(Reservation reservation);

    void addReservationExpirationScheduledOutboxEvent(Reservation reservation);

    /**
     * Closes PENDING as CANCELLED before the deadline or EXPIRED at/after it, using database time after the row lock.
     * Returns a release only for the winning transition; participates in the caller's capacity-return transaction.
     */
    Optional<CapacityRelease> closePendingOnCancellation(UUID reservationId);

    Optional<CapacityRelease> expirePending(UUID reservationId);

    record CapacityRelease(UUID eventId, int quantity) {
    }
}
