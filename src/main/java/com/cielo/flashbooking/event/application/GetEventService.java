package com.cielo.flashbooking.event.application;

import com.cielo.flashbooking.controller.error.ResourceNotFoundException;
import com.cielo.flashbooking.controller.error.ServiceUnavailableException;
import com.cielo.flashbooking.domain.event.Event;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GetEventService {

    private static final int MAX_CONCURRENT_FALLBACKS = 5;

    private final EventReader eventReader;
    private final EventAvailabilityCache cache;
    private final CacheFailureCircuit circuit;
    private final Semaphore fallbackPermits;

    @Autowired
    public GetEventService(EventReader eventReader, EventAvailabilityCache cache, Clock clock) {
        this(eventReader, cache, new CacheFailureCircuit(clock), new Semaphore(MAX_CONCURRENT_FALLBACKS, true));
    }

    GetEventService(
            EventReader eventReader,
            EventAvailabilityCache cache,
            CacheFailureCircuit circuit,
            Semaphore fallbackPermits) {
        this.eventReader = eventReader;
        this.cache = cache;
        this.circuit = circuit;
        this.fallbackPermits = fallbackPermits;
    }

    public Event get(UUID id) {
        CacheLookup lookup = readCache(id);
        if (lookup.event().isPresent()) {
            return lookup.event().get();
        }
        return loadFallback(id, lookup.cacheAvailable());
    }

    private CacheLookup readCache(UUID id) {
        if (!circuit.allowsRequest()) {
            return CacheLookup.unavailable();
        }
        try {
            Optional<Event> cached = cache.findById(id);
            circuit.recordSuccess();
            return new CacheLookup(cached, true);
        } catch (RuntimeException cacheFailure) {
            circuit.recordFailure();
            return CacheLookup.unavailable();
        }
    }

    private Event loadFallback(UUID id, boolean cacheAvailable) {
        if (!fallbackPermits.tryAcquire()) {
            throw new ServiceUnavailableException("event fallback capacity exhausted");
        }
        try {
            Event event = eventReader.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("event not found: " + id));
            if (cacheAvailable) {
                writeCache(event);
            }
            return event;
        } finally {
            fallbackPermits.release();
        }
    }

    private void writeCache(Event event) {
        if (!circuit.allowsRequest()) {
            return;
        }
        try {
            cache.put(event);
        } catch (RuntimeException cacheFailure) {
            circuit.recordFailure();
        }
    }

    private record CacheLookup(Optional<Event> event, boolean cacheAvailable) {

        private static CacheLookup unavailable() {
            return new CacheLookup(Optional.empty(), false);
        }
    }
}
