package com.cielo.flashbooking.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetReservationServiceTest {

    @Test
    void get_whenReservationExists_returnsStoredDetails() {
        UUID id = UUID.randomUUID();
        ReservationDetails stored = reservation(id);
        ReservationReader reader = mock(ReservationReader.class);
        when(reader.findById(id)).thenReturn(Optional.of(stored));

        ReservationDetails result = new GetReservationService(reader).get(id);

        assertThat(result).isEqualTo(stored);
    }

    @Test
    void get_whenReservationIsAbsent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        ReservationReader reader = mock(ReservationReader.class);
        when(reader.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetReservationService(reader).get(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_whenCalledTwice_readsThePersistencePortTwice() {
        UUID id = UUID.randomUUID();
        ReservationReader reader = mock(ReservationReader.class);
        when(reader.findById(id)).thenReturn(Optional.of(reservation(id)));
        GetReservationService service = new GetReservationService(reader);

        service.get(id);
        service.get(id);

        verify(reader, times(2)).findById(id);
    }

    @Test
    void get_whenPersistenceFails_propagatesTheFailure() {
        UUID id = UUID.randomUUID();
        ReservationReader reader = mock(ReservationReader.class);
        when(reader.findById(id)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> new GetReservationService(reader).get(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private ReservationDetails reservation(UUID id) {
        return new ReservationDetails(
                id,
                new ReservationDetails.Event(UUID.randomUUID(), "Reservation event"),
                new ReservationDetails.Customer(UUID.randomUUID(), "Ana", "ana@example.com"),
                4,
                ReservationStatus.PENDING,
                Instant.parse("2026-09-09T12:10:00Z"),
                null);
    }
}
