package com.cielo.flashbooking.event.summary;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"worker", "all"})
public class ExecutiveSummaryDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutiveSummaryDeliveryService.class);

    private final ExecutiveSummaryReportStore reportStore;
    private final DiscordSummaryPublisher publisher;
    private final ExecutiveSummaryProperties properties;

    public ExecutiveSummaryDeliveryService(
            ExecutiveSummaryReportStore reportStore,
            DiscordSummaryPublisher publisher,
            ExecutiveSummaryProperties properties) {
        this.reportStore = reportStore;
        this.publisher = publisher;
        this.properties = properties;
    }

    public void deliver(UUID eventId) {
        String secretArn = properties.getDiscord().getWebhookSecretArn();
        if (secretArn == null || secretArn.isBlank()) {
            reportStore.markDeliveryNotConfigured(eventId);
            return;
        }

        var markdown = reportStore.beginDelivery(eventId);
        if (markdown.isEmpty()) {
            return;
        }

        DiscordSummaryPublisher.DeliveryStatus result;
        try {
            result = publisher.publish(secretArn, markdown.get());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Executive summary Discord delivery is ambiguous: {}",
                    exception.getClass().getSimpleName());
            result = DiscordSummaryPublisher.DeliveryStatus.UNKNOWN;
        }
        reportStore.finishDelivery(eventId, result);
    }
}
