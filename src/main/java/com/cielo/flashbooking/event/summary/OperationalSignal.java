package com.cielo.flashbooking.event.summary;

import java.time.Instant;

public record OperationalSignal(String label, Instant occurredAt) {}
