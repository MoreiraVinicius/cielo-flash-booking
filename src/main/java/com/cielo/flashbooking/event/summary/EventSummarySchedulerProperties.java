package com.cielo.flashbooking.event.summary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "executive-summary.scheduler")
public class EventSummarySchedulerProperties {

    @Min(1)
    @Max(100)
    private int batchSize = 25;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
