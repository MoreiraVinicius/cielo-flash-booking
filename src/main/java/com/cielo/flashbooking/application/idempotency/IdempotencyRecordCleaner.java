package com.cielo.flashbooking.application.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"worker", "all"})
public class IdempotencyRecordCleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyRecordCleaner.class);

    private final IdempotencyStore idempotencyStore;
    private final IdempotencyCleanupProperties properties;

    public IdempotencyRecordCleaner(
            IdempotencyStore idempotencyStore,
            IdempotencyCleanupProperties properties) {
        this.idempotencyStore = idempotencyStore;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${idempotency.cleanup.fixed-delay:5s}",
            initialDelayString = "${idempotency.cleanup.initial-delay:5s}")
    public int deleteExpiredRecords() {
        int deleted = idempotencyStore.deleteExpired(properties.batchSize());
        if (deleted > 0) {
            LOGGER.info("Deleted {} expired idempotency records", deleted);
        }
        return deleted;
    }
}
