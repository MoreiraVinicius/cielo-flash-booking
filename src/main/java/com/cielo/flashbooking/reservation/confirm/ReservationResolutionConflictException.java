package com.cielo.flashbooking.reservation.confirm;

public class ReservationResolutionConflictException extends RuntimeException {

    public ReservationResolutionConflictException() {
        super("resolution identity was reused with a different payload");
    }
}
