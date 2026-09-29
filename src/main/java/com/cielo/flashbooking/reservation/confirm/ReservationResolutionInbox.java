package com.cielo.flashbooking.reservation.confirm;

import java.util.Optional;

public interface ReservationResolutionInbox {

    boolean tryBegin(ReservationResolutionMessage message, String payloadFingerprint);

    Optional<StoredResolution> find(String source, String resolutionId);

    void complete(String source, String resolutionId, ReservationResolutionOutcome outcome);

    record StoredResolution(String payloadFingerprint, ReservationResolutionOutcome outcome) {}
}
