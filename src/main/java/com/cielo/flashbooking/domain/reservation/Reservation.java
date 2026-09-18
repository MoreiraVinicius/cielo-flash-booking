package com.cielo.flashbooking.domain.reservation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Reservation {

    private final UUID id;
    private final UUID eventId;
    private final UUID customerId;
    private final int quantity;
    private final Instant expiresAt;
    private final Instant createdAt;

    private Reservation(
            UUID id,
            UUID eventId,
            UUID customerId,
            int quantity,
            Instant expiresAt,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public static Reservation pending(
            UUID id, UUID eventId, Customer customer, int quantity, Instant expiresAt, Instant createdAt) {
        return new Reservation(id, eventId, Objects.requireNonNull(customer, "customer must not be null").id(), quantity, expiresAt, createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID eventId() {
        return eventId;
    }

    public UUID customerId() {
        return customerId;
    }

    public int quantity() {
        return quantity;
    }

    public ReservationStatus status() {
        return ReservationStatus.PENDING;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public ClosureReason closureReason() {
        return null;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return createdAt;
    }
}
