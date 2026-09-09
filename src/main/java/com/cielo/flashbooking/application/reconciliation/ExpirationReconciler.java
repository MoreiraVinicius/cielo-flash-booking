package com.cielo.flashbooking.application.reconciliation;

import com.cielo.flashbooking.feature.reservation.expire.ExpireReservationService;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpirationReconciler {

    private static final int BATCH_SIZE = 100;

    private final ReservationReader reservationReader;
    private final ExpireReservationService expireReservationService;

    public ExpirationReconciler(
            ReservationReader reservationReader,
            ExpireReservationService expireReservationService) {
        this.reservationReader = reservationReader;
        this.expireReservationService = expireReservationService;
    }

    @Scheduled(fixedDelay = 1000)
    public void reconcile() {
        reservationReader.findExpiredPendingIds(BATCH_SIZE).forEach(expireReservationService::expire);
    }
}
