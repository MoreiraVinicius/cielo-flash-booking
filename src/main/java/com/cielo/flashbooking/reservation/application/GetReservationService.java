package com.cielo.flashbooking.reservation.application;

import com.cielo.flashbooking.controller.error.ResourceNotFoundException;
import com.cielo.flashbooking.controller.error.ServiceUnavailableException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GetReservationService {

    private static final int MAX_CONCURRENT_FALLBACKS = 5;

    private final ReservationReader reservationReader;
    private final ReservationCache cache;
    private final ReservationCacheFailureCircuit circuit;
    private final Semaphore fallbackPermits;

    @Autowired
    public GetReservationService(ReservationReader reservationReader, ReservationCache cache, Clock clock) {
        this(reservationReader, cache, new ReservationCacheFailureCircuit(clock), new Semaphore(MAX_CONCURRENT_FALLBACKS, true));
    }

    GetReservationService(
            ReservationReader reservationReader,
            ReservationCache cache,
            ReservationCacheFailureCircuit circuit,
            Semaphore fallbackPermits) {
        this.reservationReader = reservationReader;
        this.cache = cache;
        this.circuit = circuit;
        this.fallbackPermits = fallbackPermits;
    }

    public ReservationDetails get(UUID id) {
        CacheLookup lookup = readCache(id);
        if (lookup.reservation().isPresent()) {
            return lookup.reservation().get();
        }
        return loadFallback(id, lookup.cacheAvailable());
    }

    private CacheLookup readCache(UUID id) {
        if (!circuit.allowsRequest()) {
            return CacheLookup.unavailable();
        }
        try {
            Optional<ReservationDetails> cached = cache.findById(id);
            circuit.recordSuccess();
            return new CacheLookup(cached, true);
        } catch (RuntimeException cacheFailure) {
            circuit.recordFailure();
            return CacheLookup.unavailable();
        }
    }

    private ReservationDetails loadFallback(UUID id, boolean cacheAvailable) {
        if (!fallbackPermits.tryAcquire()) {
            throw new ServiceUnavailableException("reservation fallback capacity exhausted");
        }
        try {
            ReservationDetails reservation = reservationReader.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("reservation not found: " + id));
            if (cacheAvailable) {
                writeCache(reservation);
            }
            return reservation;
        } finally {
            fallbackPermits.release();
        }
    }

    private void writeCache(ReservationDetails reservation) {
        if (!circuit.allowsRequest()) {
            return;
        }
        try {
            cache.put(reservation);
        } catch (RuntimeException cacheFailure) {
            circuit.recordFailure();
        }
    }

    private record CacheLookup(Optional<ReservationDetails> reservation, boolean cacheAvailable) {

        private static CacheLookup unavailable() {
            return new CacheLookup(Optional.empty(), false);
        }
    }
}
