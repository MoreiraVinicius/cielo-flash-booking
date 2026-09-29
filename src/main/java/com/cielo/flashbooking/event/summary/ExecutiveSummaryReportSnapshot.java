package com.cielo.flashbooking.event.summary;

import java.time.Instant;

public record ExecutiveSummaryReportSnapshot(
        String status, String markdown, Instant generatedAt, Instant asOf, String deliveryStatus) {}
