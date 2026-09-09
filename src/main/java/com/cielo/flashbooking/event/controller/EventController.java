package com.cielo.flashbooking.event.controller;

import com.cielo.flashbooking.application.idempotency.IdempotencyCommand;
import com.cielo.flashbooking.application.idempotency.IdempotencyResponse;
import com.cielo.flashbooking.application.idempotency.IdempotencyResult;
import com.cielo.flashbooking.application.idempotency.PersistentIdempotencyService;
import com.cielo.flashbooking.controller.error.ProblemResponseFactory;
import com.cielo.flashbooking.event.application.CreateEventService;
import com.cielo.flashbooking.event.application.GetEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventController {

    private final CreateEventService createEventService;
    private final GetEventService getEventService;
    private final PersistentIdempotencyService idempotencyService;
    private final ProblemResponseFactory problemResponseFactory;
    private final ObjectMapper objectMapper;

    public EventController(
            CreateEventService createEventService,
            GetEventService getEventService,
            PersistentIdempotencyService idempotencyService,
            ProblemResponseFactory problemResponseFactory,
            ObjectMapper objectMapper) {
        this.createEventService = createEventService;
        this.getEventService = getEventService;
        this.idempotencyService = idempotencyService;
        this.problemResponseFactory = problemResponseFactory;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    ResponseEntity<String> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateEventRequest request,
            HttpServletRequest servletRequest) {
        IdempotencyResult result = idempotencyService.execute(
                IdempotencyCommand.from(idempotencyKey, "POST", "/events", request, objectMapper),
                () -> new IdempotencyResponse(
                        201, EventResponse.from(createEventService.create(request.name(), request.capacity()))),
                exception -> {
                    var problem = problemResponseFactory.expectedFailure(exception, servletRequest);
                    return new IdempotencyResponse(problem.getStatus(), problem);
                });
        return response(result, "/events/");
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable UUID id) {
        return EventResponse.from(getEventService.get(id));
    }

    private ResponseEntity<String> response(IdempotencyResult result, String locationPrefix) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status())
                .contentType(result.status() >= 400 ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON);
        if (result.status() == 201) {
            try {
                builder.location(URI.create(locationPrefix + objectMapper.readTree(result.responseBody()).get("id").asText()));
            } catch (Exception exception) {
                throw new IllegalStateException("could not read idempotency response location", exception);
            }
        }
        return builder.body(result.responseBody());
    }
}
