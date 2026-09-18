package com.cielo.flashbooking.application.reconciliation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.feature.reservation.expire.ExpireReservationService;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpirationReconcilerTest {

    @Test
    void reconcile_whenPostgresqlFindsExpiredPendingReservations_reappliesExpirationForEachCandidate() {
        UUID firstReservationId = UUID.randomUUID();
        UUID secondReservationId = UUID.randomUUID();
        ReservationReader reader = mock(ReservationReader.class);
        ExpireReservationService expirationService = mock(ExpireReservationService.class);
        when(reader.findExpiredPendingIds(1000)).thenReturn(List.of(firstReservationId, secondReservationId));

        new ExpirationReconciler(
                reader, expirationService, new ExpirationReconciliationProperties(null)).reconcile();

        verify(expirationService).expire(firstReservationId);
        verify(expirationService).expire(secondReservationId);
    }
}
