package com.cielo.flashbooking.event.application;

import com.cielo.flashbooking.application.error.ResourceNotFoundException;
import com.cielo.flashbooking.application.error.ServiceUnavailableException;
import com.cielo.flashbooking.domain.event.Event;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GetEventService {

    private static final int MAX_CONCURRENT_FALLBACKS = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger(GetEventService.class);

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
            LOGGER.warn("Event availability cache lookup failed eventId={}", id, cacheFailure);
            circuit.recordFailure();
            return CacheLookup.unavailable();
        }
    }

    private Event loadFallback(UUID id, boolean cacheAvailable) {
        if (cacheAvailable) {
            return loadFromDatabase(id, true);
        }
        if (!fallbackPermits.tryAcquire()) {
            throw new ServiceUnavailableException("event fallback capacity exhausted");
        }
        try {
            return loadFromDatabase(id, false);
        } finally {
            fallbackPermits.release();
        }
    }

    private Event loadFromDatabase(UUID id, boolean populateCache) {
        Event event = eventReader.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("event not found: " + id));
        if (populateCache) {
            writeCache(event);
        }
        return event;
    }

    private void writeCache(Event event) {
        if (!circuit.allowsRequest()) {
            return;
        }
        try {
            cache.put(event);
        } catch (RuntimeException cacheFailure) {
            LOGGER.warn("Event availability cache write failed eventId={}", event.id(), cacheFailure);
            circuit.recordFailure();
        }
    }

    private record CacheLookup(Optional<Event> event, boolean cacheAvailable) {

        private static CacheLookup unavailable() {
            return new CacheLookup(Optional.empty(), false);
        }
    }
}
