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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ConfirmReservationServiceTest {

    @Test
    void confirm_whenReservationIsEligible_commitsWithoutChangingInventory() {
        UUID reservationId = UUID.randomUUID();
        Instant confirmedAt = Instant.parse("2026-09-29T12:00:00Z");
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.confirmPending(reservationId))
                .thenReturn(Optional.of(new ReservationWriter.ConfirmationTransition(
                        ReservationStatus.CONFIRMED, confirmedAt, null, true)));

        assertThat(service(writer, inventory, publisher).confirm(reservationId))
                .contains(new ReservationConfirmationResult(ReservationStatus.CONFIRMED, confirmedAt, true));

        verify(inventory, never()).increment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void confirm_whenDeadlinePassed_releasesCapacityExactlyOnceAndInvalidatesAvailability() {
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.confirmPending(reservationId))
                .thenReturn(Optional.of(new ReservationWriter.ConfirmationTransition(
                        ReservationStatus.EXPIRED, null, new ReservationWriter.CapacityRelease(eventId, 3), true)));
        when(inventory.increment(eventId, 3)).thenReturn(true);

        assertThat(service(writer, inventory, publisher).confirm(reservationId))
                .contains(new ReservationConfirmationResult(ReservationStatus.EXPIRED, null, true));

        verify(inventory).increment(eventId, 3);
        verify(publisher).publishEvent(new EventAvailabilityChanged(eventId));
    }

    @Test
    void confirm_whenCapacityCannotBeReleased_failsTheTransaction() {
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.confirmPending(reservationId))
                .thenReturn(Optional.of(new ReservationWriter.ConfirmationTransition(
                        ReservationStatus.EXPIRED, null, new ReservationWriter.CapacityRelease(eventId, 3), true)));
        when(inventory.increment(eventId, 3)).thenReturn(false);

        assertThatThrownBy(() -> service(writer, inventory, publisher).confirm(reservationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("could not return expired reservation capacity");

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void confirm_whenReservationDoesNotExist_returnsEmptyWithoutInventoryEffects() {
        UUID reservationId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.confirmPending(reservationId)).thenReturn(Optional.empty());

        assertThat(service(writer, inventory, publisher).confirm(reservationId)).isEmpty();

        verify(inventory, never()).increment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    private ConfirmReservationService service(
            ReservationWriter writer, InventoryOperations inventory, ApplicationEventPublisher publisher) {
        return new ConfirmReservationService(writer, inventory, publisher);
    }
}
