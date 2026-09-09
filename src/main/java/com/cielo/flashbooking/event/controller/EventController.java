package com.cielo.flashbooking.event.controller;

import com.cielo.flashbooking.event.application.CreateEventService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventController {

    private final CreateEventService createEventService;

    public EventController(CreateEventService createEventService) {
        this.createEventService = createEventService;
    }

    @PostMapping
    ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = EventResponse.from(createEventService.create(request.name(), request.capacity()));
        return ResponseEntity.created(URI.create("/events/" + response.id())).body(response);
    }
}
