package com.cielo.flashbooking.event.summary;

import java.util.Optional;

public interface ExecutiveNarrative {

    Optional<Narrative> write(EventSummaryFacts facts);

    record Narrative(String text, String modelId, int inputTokens, int outputTokens) {}
}
