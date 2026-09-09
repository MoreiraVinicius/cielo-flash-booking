package com.cielo.flashbooking.event.controller;

import com.cielo.flashbooking.event.application.CreateEventService;
import com.cielo.flashbooking.event.application.GetEventService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventController {

    private final CreateEventService createEventService;
    private final GetEventService getEventService;

    public EventController(CreateEventService createEventService, GetEventService getEventService) {
        this.createEventService = createEventService;
        this.getEventService = getEventService;
    }

    @PostMapping
    ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = EventResponse.from(createEventService.create(request.name(), request.capacity()));
        return ResponseEntity.created(URI.create("/events/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable UUID id) {
        return EventResponse.from(getEventService.get(id));
    }
}
