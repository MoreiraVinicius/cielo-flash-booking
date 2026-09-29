package com.cielo.flashbooking.event.summary;

import java.util.List;

public record OperationalSignalResult(Status status, List<OperationalSignal> signals) {

    public enum Status {
        OBSERVED,
        EMPTY,
        PARTIAL,
        UNAVAILABLE
    }

    public OperationalSignalResult {
        signals = List.copyOf(signals);
    }

    public static OperationalSignalResult empty() {
        return new OperationalSignalResult(Status.EMPTY, List.of());
    }

    public static OperationalSignalResult unavailable() {
        return new OperationalSignalResult(Status.UNAVAILABLE, List.of());
    }
}
