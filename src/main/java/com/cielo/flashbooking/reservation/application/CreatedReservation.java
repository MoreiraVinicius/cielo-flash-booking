package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.domain.reservation.Customer;
import com.cielo.flashbooking.domain.reservation.Reservation;
import java.util.Objects;

public record CreatedReservation(Reservation reservation, Customer customer) {

    public CreatedReservation {
        Objects.requireNonNull(reservation, "reservation must not be null");
        Objects.requireNonNull(customer, "customer must not be null");
    }
}
