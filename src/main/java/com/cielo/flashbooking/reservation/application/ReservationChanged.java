package com.cielo.flashbooking.reservation.application;

import java.util.Objects;
import java.util.UUID;

public record ReservationChanged(UUID reservationId) {

    public ReservationChanged {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
    }
}
