package com.cielo.flashbooking.application.outbox;

import com.cielo.flashbooking.notification.email.NotificationDeliveryStore;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"worker", "all"})
public class OperationalDataCleaner {

    private final NotificationDeliveryStore notificationDeliveryStore;
    private final OutboxEventStore outboxEventStore;
    private final OperationalDataCleanupProperties properties;

    public OperationalDataCleaner(
            NotificationDeliveryStore notificationDeliveryStore,
            OutboxEventStore outboxEventStore,
            OperationalDataCleanupProperties properties) {
        this.notificationDeliveryStore = notificationDeliveryStore;
        this.outboxEventStore = outboxEventStore;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${operational-data.cleanup.fixed-delay:1h}",
            initialDelayString = "${operational-data.cleanup.initial-delay:1h}")
    public void clean() {
        notificationDeliveryStore.deleteTerminal(properties.batchSize(), properties.retention());
        outboxEventStore.deletePublished(properties.batchSize(), properties.retention());
    }
}
