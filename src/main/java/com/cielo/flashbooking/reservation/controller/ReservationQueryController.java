package com.cielo.flashbooking.reservation.controller;

import com.cielo.flashbooking.reservation.application.CancelReservationService;
import com.cielo.flashbooking.reservation.application.GetReservationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
class ReservationQueryController {

    private final GetReservationService getReservationService;
    private final CancelReservationService cancelReservationService;

    ReservationQueryController(
            GetReservationService getReservationService, CancelReservationService cancelReservationService) {
        this.getReservationService = getReservationService;
        this.cancelReservationService = cancelReservationService;
    }

    @GetMapping("/{id}")
    ReservationDetailsResponse get(@PathVariable UUID id) {
        return ReservationDetailsResponse.from(getReservationService.get(id));
    }

    @DeleteMapping("/{id}")
    ReservationDetailsResponse cancel(@PathVariable UUID id) {
        return ReservationDetailsResponse.from(cancelReservationService.cancel(id));
    }
}
