package com.cielo.flashbooking.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.controller.error.ResourceNotFoundException;
import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetReservationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void get_whenCacheHasReservation_doesNotQueryPostgresql() {
        UUID id = UUID.randomUUID();
        ReservationDetails cached = reservation(id);
        ReservationReader reader = mock(ReservationReader.class);
        ReservationCache cache = mock(ReservationCache.class);
        when(cache.findById(id)).thenReturn(Optional.of(cached));

        ReservationDetails result = service(reader, cache).get(id);

        assertThat(result).isEqualTo(cached);
        verify(reader, never()).findById(id);
    }

    @Test
    void get_whenCacheMisses_loadsFromPostgresqlAndFillsCache() {
        UUID id = UUID.randomUUID();
        ReservationDetails stored = reservation(id);
        ReservationReader reader = mock(ReservationReader.class);
        ReservationCache cache = mock(ReservationCache.class);
        when(cache.findById(id)).thenReturn(Optional.empty());
        when(reader.findById(id)).thenReturn(Optional.of(stored));

        assertThat(service(reader, cache).get(id)).isEqualTo(stored);

        verify(cache).put(stored);
    }

    @Test
    void get_whenCacheFails_usesPostgresqlWithoutTryingToFillCache() {
        UUID id = UUID.randomUUID();
        ReservationDetails stored = reservation(id);
        ReservationReader reader = mock(ReservationReader.class);
        ReservationCache cache = mock(ReservationCache.class);
        when(cache.findById(id)).thenThrow(new IllegalStateException("cache unavailable"));
        when(reader.findById(id)).thenReturn(Optional.of(stored));

        assertThat(service(reader, cache).get(id)).isEqualTo(stored);

        verify(cache, never()).put(stored);
    }

    @Test
    void get_whenReservationIsAbsent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        ReservationReader reader = mock(ReservationReader.class);
        ReservationCache cache = mock(ReservationCache.class);
        when(cache.findById(id)).thenReturn(Optional.empty());
        when(reader.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(reader, cache).get(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private GetReservationService service(ReservationReader reader, ReservationCache cache) {
        return new GetReservationService(reader, cache, CLOCK);
    }

    private ReservationDetails reservation(UUID id) {
        return new ReservationDetails(
                id,
                new ReservationDetails.Event(UUID.randomUUID(), "Reservation event", 20, 16),
                new ReservationDetails.Customer(UUID.randomUUID(), "Ana", "ana@example.com"),
                4,
                ReservationStatus.PENDING,
                Instant.parse("2026-09-09T12:10:00Z"),
                null);
    }
}
