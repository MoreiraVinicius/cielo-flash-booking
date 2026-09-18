package com.cielo.flashbooking.application.outbox;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface OutboxEventStore {

    List<OutboxEvent> findPending(int limit);

    void recordAttempt(UUID eventId);

    void markPublished(UUID eventId, Instant publishedAt);

    int deletePublished(int limit, Duration retention);
}
