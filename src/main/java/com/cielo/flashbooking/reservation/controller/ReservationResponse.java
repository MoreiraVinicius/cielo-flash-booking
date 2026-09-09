package com.cielo.flashbooking.reservation.controller;

import com.cielo.flashbooking.domain.reservation.ClosureReason;
import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import com.cielo.flashbooking.reservation.application.CreatedReservation;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID eventId,
        CustomerResponse customer,
        int quantity,
        ReservationStatus status,
        Instant expiresAt,
        ClosureReasonResponse closureReason) {

    static ReservationResponse from(CreatedReservation createdReservation) {
        var reservation = createdReservation.reservation();
        var customer = createdReservation.customer();
        return new ReservationResponse(
                reservation.id(),
                reservation.eventId(),
                new CustomerResponse(customer.id(), customer.name(), customer.email()),
                reservation.quantity(),
                reservation.status(),
                reservation.expiresAt(),
                ClosureReasonResponse.from(reservation.closureReason()));
    }

    record CustomerResponse(UUID id, String name, String email) {
    }

    record ClosureReasonResponse(String code, String description) {

        static ClosureReasonResponse from(ClosureReason closureReason) {
            return closureReason == null
                    ? null
                    : new ClosureReasonResponse(closureReason.name(), closureReason.description());
        }
    }
}
