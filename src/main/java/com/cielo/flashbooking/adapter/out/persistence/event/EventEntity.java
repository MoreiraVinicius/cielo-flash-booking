package com.cielo.flashbooking.adapter.out.persistence.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event")
class EventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int available;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EventEntity() {
    }

    EventEntity(UUID id, String name, int capacity, int available, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.available = available;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    int capacity() {
        return capacity;
    }

    int available() {
        return available;
    }

    Instant createdAt() {
        return createdAt;
    }
}
