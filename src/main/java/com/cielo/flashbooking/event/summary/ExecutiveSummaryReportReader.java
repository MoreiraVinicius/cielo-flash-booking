package com.cielo.flashbooking.event.summary;

import java.util.Optional;
import java.util.UUID;

public interface ExecutiveSummaryReportReader {

    Optional<ExecutiveSummaryReportSnapshot> findByEventId(UUID eventId);
}
