package com.cielo.flashbooking.application.reconciliation;

import com.cielo.flashbooking.feature.reservation.expire.ExpireReservationService;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"worker", "all"})
public class ExpirationReconciler {

    private final ReservationReader reservationReader;
    private final ExpireReservationService expireReservationService;
    private final ExpirationReconciliationProperties properties;

    public ExpirationReconciler(
            ReservationReader reservationReader,
            ExpireReservationService expireReservationService,
            ExpirationReconciliationProperties properties) {
        this.reservationReader = reservationReader;
        this.expireReservationService = expireReservationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${reservation.expiration-reconciliation.fixed-delay:500ms}")
    public void reconcile() {
        reservationReader.findExpiredPendingIds(properties.batchSize()).forEach(expireReservationService::expire);
    }
}
