package com.cielo.flashbooking.application.outbox;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.cielo.flashbooking.notification.email.NotificationDeliveryStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OperationalDataCleanerTest {

    @Test
    void clean_deletesTerminalDeliveriesBeforePublishedOutboxEvents() {
        NotificationDeliveryStore deliveries = mock(NotificationDeliveryStore.class);
        OutboxEventStore outbox = mock(OutboxEventStore.class);
        Duration retention = Duration.ofDays(7);
        OperationalDataCleaner cleaner = new OperationalDataCleaner(
                deliveries, outbox, new OperationalDataCleanupProperties(500, retention));

        cleaner.clean();

        var ordered = inOrder(deliveries, outbox);
        ordered.verify(deliveries).deleteTerminal(500, retention);
        ordered.verify(outbox).deletePublished(500, retention);
    }
}
