package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import java.time.Instant;
import java.util.Objects;

public record ReservationConfirmationResult(ReservationStatus status, Instant confirmedAt) {

    public ReservationConfirmationResult {
        Objects.requireNonNull(status, "status must not be null");
        if ((status == ReservationStatus.CONFIRMED || status == ReservationStatus.CANCELLATION_PENDING)
                && confirmedAt == null) {
            throw new IllegalArgumentException("confirmed reservation status must have confirmedAt");
        }
    }
}
