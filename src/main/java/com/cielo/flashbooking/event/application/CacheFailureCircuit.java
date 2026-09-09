package com.cielo.flashbooking.event.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

final class CacheFailureCircuit {

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofSeconds(10);

    private final Clock clock;
    private final Deque<Instant> failures = new ArrayDeque<>();

    CacheFailureCircuit(Clock clock) {
        this.clock = clock;
    }

    synchronized boolean allowsRequest() {
        removeExpiredFailures();
        return failures.size() < FAILURE_THRESHOLD;
    }

    synchronized void recordSuccess() {
        removeExpiredFailures();
    }

    synchronized void recordFailure() {
        removeExpiredFailures();
        failures.addLast(clock.instant());
    }

    private void removeExpiredFailures() {
        Instant cutoff = clock.instant().minus(FAILURE_WINDOW);
        while (!failures.isEmpty() && failures.getFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }
}
