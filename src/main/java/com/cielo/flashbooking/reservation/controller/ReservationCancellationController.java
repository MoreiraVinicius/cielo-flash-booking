package com.cielo.flashbooking.reservation.controller;

import com.cielo.flashbooking.application.idempotency.IdempotencyCommand;
import com.cielo.flashbooking.application.idempotency.IdempotencyResponse;
import com.cielo.flashbooking.application.idempotency.IdempotencyResult;
import com.cielo.flashbooking.application.idempotency.PersistentIdempotencyService;
import com.cielo.flashbooking.controller.error.ProblemResponseFactory;
import com.cielo.flashbooking.reservation.application.CancelReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"command-api", "all"})
@RequestMapping("/reservations")
class ReservationCancellationController {

    private final CancelReservationService cancelReservationService;
    private final PersistentIdempotencyService idempotencyService;
    private final ProblemResponseFactory problemResponseFactory;
    private final ObjectMapper objectMapper;

    ReservationCancellationController(
            CancelReservationService cancelReservationService,
            PersistentIdempotencyService idempotencyService,
            ProblemResponseFactory problemResponseFactory,
            ObjectMapper objectMapper) {
        this.cancelReservationService = cancelReservationService;
        this.idempotencyService = idempotencyService;
        this.problemResponseFactory = problemResponseFactory;
        this.objectMapper = objectMapper;
    }

    @DeleteMapping("/{id}")
    ResponseEntity<String> cancel(
            @PathVariable UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest) {
        IdempotencyResult result = idempotencyService.execute(
                IdempotencyCommand.from(idempotencyKey, "DELETE", "/reservations/" + id, java.util.Map.of(), objectMapper),
                () -> new IdempotencyResponse(200, ReservationDetailsResponse.from(cancelReservationService.cancel(id))),
                exception -> {
                    var problem = problemResponseFactory.expectedFailure(exception, servletRequest);
                    return new IdempotencyResponse(problem.getStatus(), problem);
                });
        String responseBody = result.status() >= 400
                ? problemResponseFactory.withCurrentCorrelationId(result.responseBody(), servletRequest)
                : result.responseBody();
        return ResponseEntity.status(result.status())
                .contentType(result.status() >= 400 ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON)
                .body(responseBody);
    }
}
