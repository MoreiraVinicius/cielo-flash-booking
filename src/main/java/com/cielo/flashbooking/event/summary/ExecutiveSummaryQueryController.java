package com.cielo.flashbooking.event.summary;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"query-api", "all"})
@RequestMapping("/events/{id}/executive-summary")
public class ExecutiveSummaryQueryController {

    private final GetExecutiveSummaryService summaryService;

    public ExecutiveSummaryQueryController(GetExecutiveSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping
    ExecutiveSummaryReportSnapshot get(@PathVariable UUID id) {
        return summaryService.get(id);
    }
}
