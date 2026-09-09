package com.cielo.flashbooking.reservation.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReservationRequest(
        @NotNull @Positive Integer quantity,
        @NotNull @Valid CustomerRequest customer) {

    public record CustomerRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 320) String email) {
    }
}
