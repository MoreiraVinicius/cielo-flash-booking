package com.cielo.flashbooking.reservation.controller;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import com.cielo.flashbooking.reservation.application.ReservationDetails;
import java.time.Instant;
import java.util.UUID;

record ReservationDetailsResponse(
        UUID id,
        EventResponse event,
        CustomerResponse customer,
        int quantity,
        ReservationStatus status,
        Instant expiresAt,
        ClosureReasonResponse closureReason) {

    static ReservationDetailsResponse from(ReservationDetails reservation) {
        return new ReservationDetailsResponse(
                reservation.id(),
                new EventResponse(
                        reservation.event().id(),
                        reservation.event().name(),
                        reservation.event().capacity(),
                        reservation.event().available()),
                new CustomerResponse(
                        reservation.customer().id(),
                        reservation.customer().name(),
                        reservation.customer().email()),
                reservation.quantity(),
                reservation.status(),
                reservation.expiresAt(),
                reservation.closureReason() == null
                        ? null
                        : new ClosureReasonResponse(
                                reservation.closureReason().code(), reservation.closureReason().description()));
    }

    record EventResponse(UUID id, String name, int capacity, int available) {
    }

    record CustomerResponse(UUID id, String name, String email) {
    }

    record ClosureReasonResponse(String code, String description) {
    }
}
