package com.cielo.flashbooking.reservation.controller;

import com.cielo.flashbooking.reservation.application.CreateReservationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events/{eventId}/reservations")
public class ReservationController {

    private final CreateReservationService createReservationService;

    public ReservationController(CreateReservationService createReservationService) {
        this.createReservationService = createReservationService;
    }

    @PostMapping
    ResponseEntity<ReservationResponse> create(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = ReservationResponse.from(createReservationService.create(
                eventId,
                request.quantity(),
                request.customer().name(),
                request.customer().email()));
        return ResponseEntity.created(URI.create("/reservations/" + response.id())).body(response);
    }
}
