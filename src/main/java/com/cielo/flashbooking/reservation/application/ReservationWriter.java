package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.domain.reservation.Customer;
import com.cielo.flashbooking.domain.reservation.Reservation;
import java.util.UUID;

public interface ReservationWriter {

    boolean eventExists(UUID eventId);

    Customer upsertCustomer(Customer customer);

    void save(Reservation reservation);

    void addReservationCreatedOutboxEvent(Reservation reservation);
}
