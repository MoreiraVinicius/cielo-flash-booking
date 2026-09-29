package com.cielo.flashbooking.event.summary;

import java.time.Instant;

public interface OperationalSignalsReader {

    OperationalSignalResult read(Instant from, Instant to);
}
