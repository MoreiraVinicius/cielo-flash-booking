package com.cielo.flashbooking.application.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(UUID id, String eventType, String payload, Instant occurredAt) {
}
