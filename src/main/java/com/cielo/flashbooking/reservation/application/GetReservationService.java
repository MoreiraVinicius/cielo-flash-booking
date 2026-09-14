package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetReservationService {

    private final ReservationReader reservationReader;

    public GetReservationService(ReservationReader reservationReader) {
        this.reservationReader = reservationReader;
    }

    public ReservationDetails get(UUID id) {
        return reservationReader.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("reservation not found: " + id));
    }
}
