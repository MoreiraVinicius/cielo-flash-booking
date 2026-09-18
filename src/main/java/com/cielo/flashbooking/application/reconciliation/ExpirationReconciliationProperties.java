package com.cielo.flashbooking.application.reconciliation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "reservation.expiration-reconciliation")
public record ExpirationReconciliationProperties(@Min(1) @Max(10_000) Integer batchSize) {

    public ExpirationReconciliationProperties {
        batchSize = batchSize == null ? 1_000 : batchSize;
    }
}
