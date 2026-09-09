package com.cielo.flashbooking.reservation.application;

import java.util.Optional;
import java.util.UUID;

public interface ReservationReader {

    Optional<ReservationDetails> findById(UUID id);
}
