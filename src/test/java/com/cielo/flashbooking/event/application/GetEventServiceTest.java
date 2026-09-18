package com.cielo.flashbooking.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.application.error.ServiceUnavailableException;
import com.cielo.flashbooking.domain.event.Event;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GetEventServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void returnsCacheHitWithoutQueryingPostgresql() {
        UUID id = UUID.randomUUID();
        Event cached = event(id);
        EventReader reader = mock(EventReader.class);
        EventAvailabilityCache cache = mock(EventAvailabilityCache.class);
        when(cache.findById(id)).thenReturn(Optional.of(cached));
        GetEventService service = new GetEventService(reader, cache, CLOCK);

        Event result = service.get(id);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(reader);
    }

    @Test
    void populatesCacheAfterPostgresqlFallbackOnMiss() {
        UUID id = UUID.randomUUID();
        Event stored = event(id);
        EventReader reader = mock(EventReader.class);
        EventAvailabilityCache cache = mock(EventAvailabilityCache.class);
        when(cache.findById(id)).thenReturn(Optional.empty());
        when(reader.findById(id)).thenReturn(Optional.of(stored));
        GetEventService service = new GetEventService(reader, cache, CLOCK);

        Event result = service.get(id);

        assertThat(result).isSameAs(stored);
        verify(reader).findById(id);
        verify(cache).put(stored);
    }

    @Test
    void cacheMiss_doesNotConsumeOutageFallbackPermit() {
        UUID id = UUID.randomUUID();
        Event stored = event(id);
        EventReader reader = ignored -> Optional.of(stored);
        EventAvailabilityCache cache = mock(EventAvailabilityCache.class);
        when(cache.findById(id)).thenReturn(Optional.empty());
        GetEventService service = new GetEventService(
                reader, cache, new CacheFailureCircuit(CLOCK), new Semaphore(0));

        assertThat(service.get(id)).isSameAs(stored);
        verify(cache).put(stored);
    }

    @Test
    void successfulCacheOperation_resetsPriorFailureWindow() {
        CacheFailureCircuit circuit = new CacheFailureCircuit(CLOCK);
        for (int attempt = 0; attempt < 5; attempt++) {
            circuit.recordFailure();
        }
        assertThat(circuit.allowsRequest()).isFalse();

        circuit.recordSuccess();

        assertThat(circuit.allowsRequest()).isTrue();
    }

    @Test
    void opensCircuitAfterFiveCacheFailuresAndKeepsUsingPostgresql() {
        UUID id = UUID.randomUUID();
        Event stored = event(id);
        EventReader reader = mock(EventReader.class);
        EventAvailabilityCache cache = mock(EventAvailabilityCache.class);
        when(cache.findById(id)).thenThrow(new IllegalStateException("cache unavailable"));
        when(reader.findById(id)).thenReturn(Optional.of(stored));
        GetEventService service = new GetEventService(reader, cache, CLOCK);

        for (int attempt = 0; attempt < 6; attempt++) {
            assertThat(service.get(id)).isSameAs(stored);
        }

        verify(cache, times(5)).findById(id);
        verify(cache, never()).put(stored);
        verify(reader, times(6)).findById(id);
    }

    @Test
    void limitsConcurrentPostgresqlFallbacksToFive() throws Exception {
        EventAvailabilityCache cache = mock(EventAvailabilityCache.class);
        when(cache.findById(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("cache unavailable"));
        CountDownLatch fiveReadersEntered = new CountDownLatch(5);
        CountDownLatch releaseReaders = new CountDownLatch(1);
        AtomicInteger activeReaders = new AtomicInteger();
        AtomicInteger maximumReaders = new AtomicInteger();
        EventReader blockingReader = id -> {
            int active = activeReaders.incrementAndGet();
            maximumReaders.accumulateAndGet(active, Math::max);
            fiveReadersEntered.countDown();
            try {
                if (!releaseReaders.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test reader was not released");
                }
                return Optional.of(event(id));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test interrupted", interrupted);
            } finally {
                activeReaders.decrementAndGet();
            }
        };
        GetEventService service = new GetEventService(blockingReader, cache, CLOCK);
        var executor = Executors.newFixedThreadPool(5);
        var readers = new ArrayList<java.util.concurrent.Future<Event>>();

        try {
            for (int index = 0; index < 5; index++) {
                readers.add(executor.submit(() -> service.get(UUID.randomUUID())));
            }
            assertThat(fiveReadersEntered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                    .isInstanceOf(ServiceUnavailableException.class);
            assertThat(maximumReaders).hasValue(5);
        } finally {
            releaseReaders.countDown();
            for (var reader : readers) {
                reader.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
        }
    }

    private static Event event(UUID id) {
        return Event.restore(id, "Flash sale", 100, 75, CLOCK.instant());
    }
}
