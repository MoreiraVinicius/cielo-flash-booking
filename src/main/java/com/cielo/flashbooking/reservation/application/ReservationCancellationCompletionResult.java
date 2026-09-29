package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import java.util.Objects;
import java.util.UUID;

public record ReservationCancellationCompletionResult(ReservationStatus status, UUID cancellationId, boolean changed) {

    public ReservationCancellationCompletionResult {
        Objects.requireNonNull(status, "status must not be null");
    }
}
