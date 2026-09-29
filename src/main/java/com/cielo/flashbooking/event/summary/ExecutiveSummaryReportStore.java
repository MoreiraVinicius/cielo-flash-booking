package com.cielo.flashbooking.event.summary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutiveSummaryReportStore {

    List<UUID> findDueEventIds(int limit);

    Optional<ClaimedExecutiveSummary> claim(UUID eventId);

    void complete(
            ClaimedExecutiveSummary claim,
            String markdown,
            boolean ready,
            String modelId,
            Integer inputTokens,
            Integer outputTokens,
            String errorCode);

    void markDeliveryNotConfigured(UUID eventId);

    Optional<String> beginDelivery(UUID eventId);

    void finishDelivery(UUID eventId, DiscordSummaryPublisher.DeliveryStatus status);
}
