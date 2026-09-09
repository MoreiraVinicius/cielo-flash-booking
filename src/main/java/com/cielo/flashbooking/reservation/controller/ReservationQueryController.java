package com.cielo.flashbooking.reservation.controller;

import com.cielo.flashbooking.reservation.application.GetReservationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
class ReservationQueryController {

    private final GetReservationService getReservationService;

    ReservationQueryController(GetReservationService getReservationService) {
        this.getReservationService = getReservationService;
    }

    @GetMapping("/{id}")
    ReservationDetailsResponse get(@PathVariable UUID id) {
        return ReservationDetailsResponse.from(getReservationService.get(id));
    }
}
