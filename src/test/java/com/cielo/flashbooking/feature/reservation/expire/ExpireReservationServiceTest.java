package com.cielo.flashbooking.feature.reservation.expire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.event.application.EventAvailabilityChanged;
import com.cielo.flashbooking.inventory.application.InventoryOperations;
import com.cielo.flashbooking.reservation.application.ReservationWriter;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ExpireReservationServiceTest {

    @Test
    void expire_whenPostgresqlMarksPendingReservationExpired_returnsCapacityAndInvalidatesEventCache() {
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.expirePending(reservationId)).thenReturn(Optional.of(new ReservationWriter.CapacityRelease(eventId, 3)));
        when(inventory.increment(eventId, 3)).thenReturn(true);

        assertThat(service(writer, inventory, publisher).expire(reservationId)).isTrue();

        verify(inventory).increment(eventId, 3);
        verify(publisher).publishEvent(new EventAvailabilityChanged(eventId));
    }

    @Test
    void expire_whenPostgresqlRejectsAnEarlyOrDuplicateMessage_leavesCapacityUntouched() {
        UUID reservationId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(writer.expirePending(reservationId)).thenReturn(Optional.empty());

        assertThat(service(writer, inventory, publisher).expire(reservationId)).isFalse();

        verify(inventory, never()).increment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void expire_whenCapacityCannotBeReturned_rollsBackTheStateTransition() {
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ReservationWriter writer = mock(ReservationWriter.class);
        InventoryOperations inventory = mock(InventoryOperations.class);
        when(writer.expirePending(reservationId)).thenReturn(Optional.of(new ReservationWriter.CapacityRelease(eventId, 3)));
        when(inventory.increment(eventId, 3)).thenReturn(false);

        assertThatThrownBy(() -> service(writer, inventory, mock(ApplicationEventPublisher.class)).expire(reservationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("could not return expired reservation capacity");
    }

    private ExpireReservationService service(
            ReservationWriter writer,
            InventoryOperations inventory,
            ApplicationEventPublisher publisher) {
        return new ExpireReservationService(writer, inventory, publisher);
    }
}
