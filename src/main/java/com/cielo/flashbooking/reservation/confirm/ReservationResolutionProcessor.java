package com.cielo.flashbooking.reservation.confirm;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import com.cielo.flashbooking.reservation.application.CompleteReservationCancellationService;
import com.cielo.flashbooking.reservation.application.ConfirmReservationService;
import com.cielo.flashbooking.reservation.application.ReservationCancellationCompletionResult;
import com.cielo.flashbooking.reservation.application.ReservationConfirmationResult;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationResolutionProcessor {

    private final ReservationResolutionInbox inbox;
    private final ConfirmReservationService confirmReservationService;
    private final CompleteReservationCancellationService completeReservationCancellationService;

    public ReservationResolutionProcessor(
            ReservationResolutionInbox inbox,
            ConfirmReservationService confirmReservationService,
            CompleteReservationCancellationService completeReservationCancellationService) {
        this.inbox = inbox;
        this.confirmReservationService = confirmReservationService;
        this.completeReservationCancellationService = completeReservationCancellationService;
    }

    @Transactional
    public ReservationResolutionOutcome process(ReservationResolutionMessage message, String payloadFingerprint) {
        if (!inbox.tryBegin(message, payloadFingerprint)) {
            ReservationResolutionInbox.StoredResolution stored = inbox.find(message.source(), message.resolutionId())
                    .orElseThrow(() -> new IllegalStateException("inbox identity disappeared after conflict"));
            if (!stored.payloadFingerprint().equals(payloadFingerprint)) {
                throw new ReservationResolutionConflictException();
            }
            if (stored.outcome() == null || "PROCESSING".equals(stored.outcome().code())) {
                throw new IllegalStateException("committed inbox row has no completed outcome");
            }
            return stored.outcome();
        }

        ReservationResolutionOutcome outcome = decide(message);
        inbox.complete(message.source(), message.resolutionId(), outcome);
        return outcome;
    }

    private ReservationResolutionOutcome decide(ReservationResolutionMessage message) {
        return switch (message.type()) {
            case CONFIRMATION_REQUESTED -> confirm(message);
            case CANCELLATION_COMPLETED -> completeCancellation(message);
        };
    }

    private ReservationResolutionOutcome confirm(ReservationResolutionMessage message) {
        Optional<ReservationConfirmationResult> result = confirmReservationService.confirm(message.reservationId());
        if (result.isEmpty()) {
            return outcome("NOT_FOUND", null, null, null);
        }

        ReservationConfirmationResult confirmation = result.orElseThrow();
        String code =
                switch (confirmation.status()) {
                    case CONFIRMED -> confirmation.changed() ? "CONFIRMED" : "ALREADY_CONFIRMED";
                    case EXPIRED -> "EXPIRED";
                    case CANCELLED -> "CANCELLED";
                    case CANCELLATION_PENDING -> "CANCELLATION_PENDING";
                    case PENDING -> throw new IllegalStateException("confirmation decision left reservation pending");
                };
        return outcome(code, confirmation.status(), confirmation.confirmedAt(), null);
    }

    private ReservationResolutionOutcome completeCancellation(ReservationResolutionMessage message) {
        Optional<ReservationCancellationCompletionResult> result =
                completeReservationCancellationService.complete(message.reservationId(), message.cancellationId());
        if (result.isEmpty()) {
            return outcome("NOT_FOUND", null, null, message.cancellationId());
        }

        ReservationCancellationCompletionResult completion = result.orElseThrow();
        String code;
        if (completion.changed()
                || (completion.status() == ReservationStatus.CANCELLED
                        && message.cancellationId().equals(completion.cancellationId()))) {
            code = "CANCELLED";
        } else if (completion.status() == ReservationStatus.CANCELLATION_PENDING) {
            code = "CANCELLATION_ID_MISMATCH";
        } else if (completion.status() == ReservationStatus.CANCELLED) {
            code = "CANCELLATION_ID_MISMATCH";
        } else {
            code = "CANCELLATION_NOT_PENDING";
        }
        return outcome(code, completion.status(), null, message.cancellationId());
    }

    private ReservationResolutionOutcome outcome(
            String code, ReservationStatus status, java.time.Instant confirmedAt, java.util.UUID cancellationId) {
        return new ReservationResolutionOutcome(code, status, confirmedAt, cancellationId);
    }
}
