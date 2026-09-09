package com.cielo.flashbooking.reservation.application;

import java.util.Optional;
import java.util.UUID;

public interface ReservationCache {

    Optional<ReservationDetails> findById(UUID id);

    void put(ReservationDetails reservation);

    void evict(UUID id);
}
