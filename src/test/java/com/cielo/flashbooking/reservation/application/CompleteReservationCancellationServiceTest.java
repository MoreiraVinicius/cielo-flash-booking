package com.cielo.flashbooking.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CompleteReservationCancellationServiceTest {

    @Test
    void complete_whenCancellationIdMatches_returnsCapacityAndInvalidatesAvailability() {
        UUID reservationId = UUID.randomUUID();
        UUID cancellationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.completeConfirmedCancellation(reservationId, cancellationId))
                .thenReturn(Optional.of(new ReservationWriter.CancellationCompletionTransition(
                        ReservationStatus.CANCELLED,
                        cancellationId,
                        true,
                        new ReservationWriter.CapacityRelease(eventId, 3, ReservationStatus.CANCELLED))));
        when(inventory.increment(eventId, 3)).thenReturn(true);

        assertThat(new CompleteReservationCancellationService(writer, inventory, publisher)
                        .complete(reservationId, cancellationId))
                .contains(
                        new ReservationCancellationCompletionResult(ReservationStatus.CANCELLED, cancellationId, true));

        verify(inventory).increment(eventId, 3);
        verify(publisher).publishEvent(new EventAvailabilityChanged(eventId));
    }

    @Test
    void complete_whenNoCancellationTransitionWins_doesNotReturnCapacity() {
        UUID reservationId = UUID.randomUUID();
        UUID cancellationId = UUID.randomUUID();
        UUID activeCancellationId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.completeConfirmedCancellation(reservationId, cancellationId))
                .thenReturn(Optional.of(new ReservationWriter.CancellationCompletionTransition(
                        ReservationStatus.CANCELLATION_PENDING, activeCancellationId, false, null)));

        assertThat(new CompleteReservationCancellationService(writer, inventory, publisher)
                        .complete(reservationId, cancellationId))
                .contains(new ReservationCancellationCompletionResult(
                        ReservationStatus.CANCELLATION_PENDING, activeCancellationId, false));

        verify(inventory, never()).increment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void complete_whenCapacityCannotBeReturned_failsTheTransaction() {
        UUID reservationId = UUID.randomUUID();
        UUID cancellationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.completeConfirmedCancellation(reservationId, cancellationId))
                .thenReturn(Optional.of(new ReservationWriter.CancellationCompletionTransition(
                        ReservationStatus.CANCELLED,
                        cancellationId,
                        true,
                        new ReservationWriter.CapacityRelease(eventId, 3, ReservationStatus.CANCELLED))));
        when(inventory.increment(eventId, 3)).thenReturn(false);

        assertThatThrownBy(() -> new CompleteReservationCancellationService(writer, inventory, publisher)
                        .complete(reservationId, cancellationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("could not return cancelled reservation capacity");

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
