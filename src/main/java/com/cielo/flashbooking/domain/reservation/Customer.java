package com.cielo.flashbooking.domain.reservation;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class Customer {

    private static final int MAX_NAME_LENGTH = 200;

    private final UUID id;
    private final String name;
    private final String email;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Customer(UUID id, String name, String email, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = normalizeName(name);
        this.email = normalizeEmail(email);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Customer create(UUID id, String name, String email, Instant createdAt) {
        return new Customer(id, name, email, createdAt, createdAt);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String normalizeName(String name) {
        var normalized = Objects.requireNonNull(name, "name must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must contain between 1 and 200 characters");
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        var normalized = Objects.requireNonNull(email, "email must not be null").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("email must be valid");
        }
        return normalized;
    }
}
