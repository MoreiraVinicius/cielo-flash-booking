package com.cielo.flashbooking.reservation.confirm;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResolutionOutcome(
        String code, ReservationStatus status, Instant confirmedAt, UUID cancellationId) {}
