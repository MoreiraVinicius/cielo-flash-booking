package com.cielo.flashbooking.event.summary;

import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetExecutiveSummaryService {

    private final ExecutiveSummaryReportReader reportReader;

    public GetExecutiveSummaryService(ExecutiveSummaryReportReader reportReader) {
        this.reportReader = reportReader;
    }

    public ExecutiveSummaryReportSnapshot get(UUID eventId) {
        return reportReader
                .findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("event not found: " + eventId));
    }
}
