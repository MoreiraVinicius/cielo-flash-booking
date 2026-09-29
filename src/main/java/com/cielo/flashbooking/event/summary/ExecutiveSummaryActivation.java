package com.cielo.flashbooking.event.summary;

import java.time.Instant;

public record ExecutiveSummaryActivation(boolean enabled, Instant enabledAt, Instant updatedAt) {}
