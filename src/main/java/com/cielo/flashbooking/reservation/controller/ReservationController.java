package com.cielo.flashbooking.reservation.controller;

import com.cielo.flashbooking.application.idempotency.IdempotencyCommand;
import com.cielo.flashbooking.application.idempotency.IdempotencyResponse;
import com.cielo.flashbooking.application.idempotency.IdempotencyResult;
import com.cielo.flashbooking.application.idempotency.PersistentIdempotencyService;
import com.cielo.flashbooking.controller.error.ProblemResponseFactory;
import com.cielo.flashbooking.reservation.application.CreateReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"command-api", "all"})
@RequestMapping("/events/{eventId}/reservations")
public class ReservationController {

    private final CreateReservationService createReservationService;
    private final PersistentIdempotencyService idempotencyService;
    private final ProblemResponseFactory problemResponseFactory;
    private final ObjectMapper objectMapper;

    public ReservationController(
            CreateReservationService createReservationService,
            PersistentIdempotencyService idempotencyService,
            ProblemResponseFactory problemResponseFactory,
            ObjectMapper objectMapper) {
        this.createReservationService = createReservationService;
        this.idempotencyService = idempotencyService;
        this.problemResponseFactory = problemResponseFactory;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    ResponseEntity<String> create(
            @PathVariable UUID eventId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request,
            HttpServletRequest servletRequest) {
        IdempotencyResult result = idempotencyService.execute(
                IdempotencyCommand.from(
                        idempotencyKey,
                        "POST",
                        "/events/" + eventId + "/reservations",
                        request,
                        objectMapper),
                () -> new IdempotencyResponse(201, ReservationResponse.from(createReservationService.create(
                        eventId,
                        request.quantity(),
                        request.customer().name(),
                        request.customer().email()))),
                exception -> {
                    var problem = problemResponseFactory.expectedFailure(exception, servletRequest);
                    return new IdempotencyResponse(problem.getStatus(), problem);
                });
        try {
            String reservationId = objectMapper.readTree(result.responseBody()).get("id").asText();
            return ResponseEntity.status(result.status())
                    .location(URI.create("/reservations/" + reservationId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result.responseBody());
        } catch (Exception exception) {
            if (result.status() >= 400) {
                return ResponseEntity.status(result.status())
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problemResponseFactory.withCurrentCorrelationId(result.responseBody(), servletRequest));
            }
            throw new IllegalStateException("could not read idempotency response location", exception);
        }
    }
}
