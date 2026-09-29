package com.cielo.flashbooking.reservation.confirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import com.cielo.flashbooking.reservation.application.CompleteReservationCancellationService;
import com.cielo.flashbooking.reservation.application.ConfirmReservationService;
import com.cielo.flashbooking.reservation.application.ReservationCancellationCompletionResult;
import com.cielo.flashbooking.reservation.application.ReservationConfirmationResult;
import com.cielo.flashbooking.reservation.application.ReservationWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationResolutionProcessorTest {

    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void process_whenConfirmationIsNew_storesTheDecisionInTheInbox() {
        UUID reservationId = UUID.randomUUID();
        Instant confirmedAt = Instant.parse("2026-09-29T14:00:00Z");
        ReservationResolutionMessage message = confirmation(reservationId, "resolution-1");
        ReservationResolutionInbox inbox = mock(ReservationResolutionInbox.class);
        ConfirmReservationService confirm = mock(ConfirmReservationService.class);
        CompleteReservationCancellationService cancel = mock(CompleteReservationCancellationService.class);
        ReservationWriter writer = mock(ReservationWriter.class);
        when(inbox.tryBegin(message, FINGERPRINT)).thenReturn(true);
        when(confirm.confirm(reservationId))
                .thenReturn(
                        Optional.of(new ReservationConfirmationResult(ReservationStatus.CONFIRMED, confirmedAt, true)));

        ReservationResolutionOutcome outcome =
                new ReservationResolutionProcessor(inbox, confirm, cancel, writer).process(message, FINGERPRINT);

        assertThat(outcome)
                .isEqualTo(
                        new ReservationResolutionOutcome("CONFIRMED", ReservationStatus.CONFIRMED, confirmedAt, null));
        verify(inbox).complete(message.source(), message.resolutionId(), outcome);
        verify(writer)
                .addReservationConfirmationResultOutboxEvent(
                        reservationId,
                        message.resolutionId(),
                        "ReservationConfirmed",
                        "CONFIRMED",
                        ReservationStatus.CONFIRMED,
                        confirmedAt);
        verify(cancel, never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void process_whenSameResolutionIsRedelivered_replaysTheStoredOutcomeWithoutRepeatingTheTransition() {
        ReservationResolutionMessage message = confirmation(UUID.randomUUID(), "resolution-1");
        ReservationResolutionOutcome storedOutcome =
                new ReservationResolutionOutcome("CONFIRMED", ReservationStatus.CONFIRMED, Instant.now(), null);
        ReservationResolutionInbox inbox = mock(ReservationResolutionInbox.class);
        ConfirmReservationService confirm = mock(ConfirmReservationService.class);
        CompleteReservationCancellationService cancel = mock(CompleteReservationCancellationService.class);
        ReservationWriter writer = mock(ReservationWriter.class);
        when(inbox.tryBegin(message, FINGERPRINT)).thenReturn(false);
        when(inbox.find(message.source(), message.resolutionId()))
                .thenReturn(Optional.of(new ReservationResolutionInbox.StoredResolution(FINGERPRINT, storedOutcome)));

        assertThat(new ReservationResolutionProcessor(inbox, confirm, cancel, writer).process(message, FINGERPRINT))
                .isEqualTo(storedOutcome);

        verify(confirm, never()).confirm(org.mockito.ArgumentMatchers.any());
        verify(cancel, never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(inbox, never())
                .complete(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verify(writer, never())
                .addReservationConfirmationResultOutboxEvent(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void process_whenResolutionIdentityHasDifferentPayload_rejectsWithoutChangingReservation() {
        ReservationResolutionMessage message = confirmation(UUID.randomUUID(), "resolution-1");
        ReservationResolutionInbox inbox = mock(ReservationResolutionInbox.class);
        ConfirmReservationService confirm = mock(ConfirmReservationService.class);
        CompleteReservationCancellationService cancel = mock(CompleteReservationCancellationService.class);
        ReservationWriter writer = mock(ReservationWriter.class);
        when(inbox.tryBegin(message, FINGERPRINT)).thenReturn(false);
        when(inbox.find(message.source(), message.resolutionId()))
                .thenReturn(Optional.of(new ReservationResolutionInbox.StoredResolution(
                        "b".repeat(64),
                        new ReservationResolutionOutcome("CONFIRMED", ReservationStatus.CONFIRMED, null, null))));

        assertThatThrownBy(() -> new ReservationResolutionProcessor(inbox, confirm, cancel, writer)
                        .process(message, FINGERPRINT))
                .isInstanceOf(ReservationResolutionConflictException.class);

        verify(confirm, never()).confirm(org.mockito.ArgumentMatchers.any());
        verify(cancel, never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void process_whenExternalCancellationMatches_releasesThroughTheCancellationUseCase() {
        UUID reservationId = UUID.randomUUID();
        UUID cancellationId = UUID.randomUUID();
        ReservationResolutionMessage message = cancellation(reservationId, cancellationId, "resolution-2");
        ReservationResolutionInbox inbox = mock(ReservationResolutionInbox.class);
        ConfirmReservationService confirm = mock(ConfirmReservationService.class);
        CompleteReservationCancellationService cancel = mock(CompleteReservationCancellationService.class);
        ReservationWriter writer = mock(ReservationWriter.class);
        when(inbox.tryBegin(message, FINGERPRINT)).thenReturn(true);
        when(cancel.complete(reservationId, cancellationId))
                .thenReturn(Optional.of(new ReservationCancellationCompletionResult(
                        ReservationStatus.CANCELLED, cancellationId, true)));

        ReservationResolutionOutcome outcome =
                new ReservationResolutionProcessor(inbox, confirm, cancel, writer).process(message, FINGERPRINT);

        assertThat(outcome)
                .isEqualTo(new ReservationResolutionOutcome(
                        "CANCELLED", ReservationStatus.CANCELLED, null, cancellationId));
        verify(confirm, never()).confirm(org.mockito.ArgumentMatchers.any());
        verify(inbox).complete(message.source(), message.resolutionId(), outcome);
    }

    private ReservationResolutionMessage confirmation(UUID reservationId, String resolutionId) {
        return new ReservationResolutionMessage(
                ReservationResolutionMessage.Type.CONFIRMATION_REQUESTED,
                "reservation-owner",
                resolutionId,
                reservationId,
                Instant.parse("2026-09-29T14:00:00Z"),
                null);
    }

    private ReservationResolutionMessage cancellation(UUID reservationId, UUID cancellationId, String resolutionId) {
        return new ReservationResolutionMessage(
                ReservationResolutionMessage.Type.CANCELLATION_COMPLETED,
                "reservation-owner",
                resolutionId,
                reservationId,
                null,
                cancellationId);
    }
}
