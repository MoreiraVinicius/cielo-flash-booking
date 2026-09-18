package com.cielo.flashbooking.event.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @Positive int capacity,
        Instant startsAt,
        Instant endsAt) {
}
