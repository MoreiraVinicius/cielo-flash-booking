package com.cielo.flashbooking.application.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "operational-data.cleanup")
public record OperationalDataCleanupProperties(Integer batchSize, Duration retention) {

    public OperationalDataCleanupProperties {
        batchSize = batchSize == null ? 500 : batchSize;
        retention = retention == null ? Duration.ofDays(7) : retention;
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("operational cleanup batch size must be between 1 and 10000");
        }
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("operational cleanup retention must be positive");
        }
    }
}
