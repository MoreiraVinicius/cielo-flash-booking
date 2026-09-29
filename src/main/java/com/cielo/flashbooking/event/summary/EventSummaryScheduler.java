package com.cielo.flashbooking.event.summary;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"worker", "all"})
public class EventSummaryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventSummaryScheduler.class);

    private final ExecutiveSummaryReportStore reportStore;
    private final OperationalSignalsReader operationalSignalsReader;
    private final ExecutiveNarrative executiveNarrative;
    private final ExecutiveSummaryRenderer renderer;
    private final EventSummarySchedulerProperties properties;
    private final ExecutiveSummaryDeliveryService deliveryService;

    public EventSummaryScheduler(
            ExecutiveSummaryReportStore reportStore,
            OperationalSignalsReader operationalSignalsReader,
            ExecutiveNarrative executiveNarrative,
            ExecutiveSummaryRenderer renderer,
            EventSummarySchedulerProperties properties,
            ExecutiveSummaryDeliveryService deliveryService) {
        this.reportStore = reportStore;
        this.operationalSignalsReader = operationalSignalsReader;
        this.executiveNarrative = executiveNarrative;
        this.renderer = renderer;
        this.properties = properties;
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${executive-summary.scheduler.fixed-delay:30s}")
    public void scan() {
        reportStore.findDueEventIds(properties.getBatchSize()).forEach(this::process);
    }

    private void process(java.util.UUID eventId) {
        Optional<ClaimedExecutiveSummary> claimed = reportStore.claim(eventId);
        if (claimed.isEmpty()) {
            return;
        }

        ClaimedExecutiveSummary report = claimed.get();
        EventSummaryFacts facts = report.facts();
        OperationalSignalResult signals = OperationalSignalResult.empty();
        Optional<ExecutiveNarrative.Narrative> narrative = Optional.empty();
        String errorCode = null;
        if (facts.complete()) {
            try {
                signals = operationalSignalsReader.read(facts.saleStartsAt(), facts.endsAt());
            } catch (RuntimeException exception) {
                signals = OperationalSignalResult.unavailable();
                errorCode = "OPERATIONAL_SIGNALS_UNAVAILABLE";
                logFailure("operational signals", exception);
            }
            if (facts.acceptedTickets() > 0) {
                try {
                    narrative = executiveNarrative.write(facts);
                } catch (RuntimeException exception) {
                    errorCode = errorCode == null ? "NARRATIVE_UNAVAILABLE" : errorCode;
                    logFailure("narrative", exception);
                }
                if (narrative.isEmpty() && errorCode == null) {
                    errorCode = "NARRATIVE_UNAVAILABLE";
                }
            }
        }

        String markdown = renderer.render(
                facts,
                signals,
                narrative.map(ExecutiveNarrative.Narrative::text).orElse(null));
        boolean ready = facts.complete()
                && (facts.acceptedTickets() == 0 || narrative.isPresent())
                && signals.status() != OperationalSignalResult.Status.PARTIAL
                && signals.status() != OperationalSignalResult.Status.UNAVAILABLE;
        ExecutiveNarrative.Narrative model = narrative.orElse(null);
        reportStore.complete(
                report,
                markdown,
                ready,
                model == null ? null : model.modelId(),
                model == null ? null : model.inputTokens(),
                model == null ? null : model.outputTokens(),
                errorCode);
        deliveryService.deliver(facts.eventId());
    }

    private void logFailure(String operation, RuntimeException exception) {
        LOGGER.warn(
                "Executive summary {} unavailable: {}",
                operation,
                exception.getClass().getSimpleName());
    }
}
