package com.cielo.flashbooking.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Event {

    private static final int MAX_NAME_LENGTH = 200;

    private final UUID id;
    private final String name;
    private final int capacity;
    private final int available;
    private final Instant createdAt;
    private final Instant startsAt;
    private final Instant endsAt;

    private Event(
            UUID id,
            String name,
            int capacity,
            int available,
            Instant createdAt,
            Instant startsAt,
            Instant endsAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = normalizeName(name);
        validateCapacity(capacity);
        this.capacity = capacity;
        validateAvailable(available, capacity);
        this.available = available;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        validateSaleWindow(createdAt, startsAt, endsAt);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public static Event create(UUID id, String name, int capacity, Instant createdAt) {
        return create(id, name, capacity, createdAt, null, null);
    }

    public static Event create(
            UUID id, String name, int capacity, Instant createdAt, Instant startsAt, Instant endsAt) {
        return new Event(id, name, capacity, capacity, createdAt, startsAt, endsAt);
    }

    public static Event restore(UUID id, String name, int capacity, int available, Instant createdAt) {
        return restore(id, name, capacity, available, createdAt, null, null);
    }

    public static Event restore(
            UUID id,
            String name,
            int capacity,
            int available,
            Instant createdAt,
            Instant startsAt,
            Instant endsAt) {
        return new Event(id, name, capacity, available, createdAt, startsAt, endsAt);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int capacity() {
        return capacity;
    }

    public int available() {
        return available;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    private static String normalizeName(String name) {
        var normalized = Objects.requireNonNull(name, "name must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must contain between 1 and 200 characters");
        }
        return normalized;
    }

    private static void validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
    }

    private static void validateAvailable(int available, int capacity) {
        if (available < 0 || available > capacity) {
            throw new IllegalArgumentException("available must be between zero and capacity");
        }
    }

    private static void validateSaleWindow(Instant createdAt, Instant startsAt, Instant endsAt) {
        if (startsAt != null && !startsAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("startsAt must be after createdAt");
        }
        if (endsAt == null) {
            return;
        }
        if (startsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (startsAt == null && endsAt.isBefore(createdAt.plusSeconds(600))) {
            throw new IllegalArgumentException("end-only event must end at least ten minutes after createdAt");
        }
    }
}
