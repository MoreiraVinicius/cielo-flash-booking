package com.cielo.flashbooking.event.controller;

import com.cielo.flashbooking.event.application.GetEventService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile({"query-api", "all"})
@RequestMapping("/events")
public class EventController {

    private final GetEventService getEventService;

    public EventController(GetEventService getEventService) {
        this.getEventService = getEventService;
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable UUID id) {
        return EventResponse.from(getEventService.get(id));
    }

}
