package com.cielo.flashbooking.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.controller.error.ResourceNotFoundException;
import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CancelReservationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void cancel_whenReservationIsPending_returnsCapacityOnceAndPublishesInvalidations() {
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        ReservationReader reader = mock(ReservationReader.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ReservationDetails cancelled = cancelledReservation(reservationId, eventId);
        when(writer.cancelPending(reservationId, CLOCK.instant()))
                .thenReturn(Optional.of(new ReservationWriter.CapacityRelease(eventId, 3)));
        when(inventory.increment(eventId, 3)).thenReturn(true);
        when(reader.findById(reservationId)).thenReturn(Optional.of(cancelled));

        assertThat(service(writer, reader, inventory, publisher).cancel(reservationId)).isEqualTo(cancelled);

        verify(inventory).increment(eventId, 3);
        verify(publisher).publishEvent(new EventAvailabilityChanged(eventId));
        verify(publisher).publishEvent(new ReservationChanged(reservationId));
    }

    @Test
    void cancel_whenReservationIsAlreadyTerminal_preservesItWithoutReturningCapacityAgain() {
        UUID reservationId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        ReservationReader reader = mock(ReservationReader.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ReservationDetails terminal = cancelledReservation(reservationId, UUID.randomUUID());
        when(writer.cancelPending(reservationId, CLOCK.instant())).thenReturn(Optional.empty());
        when(reader.findById(reservationId)).thenReturn(Optional.of(terminal));

        assertThat(service(writer, reader, inventory, publisher).cancel(reservationId)).isEqualTo(terminal);

        verify(inventory, never()).increment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void cancel_whenReservationIsAbsent_returnsNotFound() {
        UUID reservationId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        ReservationReader reader = mock(ReservationReader.class);
        when(writer.cancelPending(reservationId, CLOCK.instant())).thenReturn(Optional.empty());
        when(reader.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(writer, reader, mock(InventoryOperations.class), mock(ApplicationEventPublisher.class))
                .cancel(reservationId)).isInstanceOf(ResourceNotFoundException.class);
    }

    private CancelReservationService service(
            ReservationWriter writer,
            ReservationReader reader,
            InventoryOperations inventory,
            ApplicationEventPublisher publisher) {
        return new CancelReservationService(writer, reader, inventory, CLOCK, publisher);
    }

    private ReservationDetails cancelledReservation(UUID reservationId, UUID eventId) {
        return new ReservationDetails(
                reservationId,
                new ReservationDetails.Event(eventId, "Reservation event", 10, 10),
                new ReservationDetails.Customer(UUID.randomUUID(), "Ana", "ana@example.com"),
                3,
                ReservationStatus.CANCELLED,
                Instant.parse("2026-09-09T12:10:00Z"),
                new ReservationDetails.ClosureReason("CANCELLED_BY_REQUEST", "Reserva cancelada por solicitação"));
    }
}
