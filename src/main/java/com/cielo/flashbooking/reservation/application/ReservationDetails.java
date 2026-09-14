package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReservationDetails(
        UUID id,
        Event event,
        Customer customer,
        int quantity,
        ReservationStatus status,
        Instant expiresAt,
        ClosureReason closureReason) {

    public ReservationDetails {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(customer, "customer must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public record Event(UUID id, String name) {

        public Event {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(name, "name must not be null");
        }
    }

    public record Customer(UUID id, String name, String email) {

        public Customer {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(email, "email must not be null");
        }
    }

    public record ClosureReason(String code, String description) {

        public ClosureReason {
            Objects.requireNonNull(code, "code must not be null");
            Objects.requireNonNull(description, "description must not be null");
        }
    }
}
