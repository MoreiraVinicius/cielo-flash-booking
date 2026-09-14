package com.cielo.flashbooking.application.idempotency;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "idempotency.cleanup")
public record IdempotencyCleanupProperties(
        @Min(1) @Max(10_000) int batchSize,
        Duration fixedDelay,
        Duration initialDelay) {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final Duration DEFAULT_DELAY = Duration.ofSeconds(5);

    public IdempotencyCleanupProperties {
        batchSize = batchSize == 0 ? DEFAULT_BATCH_SIZE : batchSize;
        fixedDelay = fixedDelay == null ? DEFAULT_DELAY : fixedDelay;
        initialDelay = initialDelay == null ? DEFAULT_DELAY : initialDelay;
        if (fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("idempotency cleanup fixed delay must be positive");
        }
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("idempotency cleanup initial delay cannot be negative");
        }
    }
}
