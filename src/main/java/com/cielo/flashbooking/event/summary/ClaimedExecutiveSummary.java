package com.cielo.flashbooking.event.summary;

import java.time.Instant;

public record ClaimedExecutiveSummary(EventSummaryFacts facts, String markdown, Instant generatedAt) {}
