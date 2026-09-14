package com.cielo.flashbooking.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.domain.reservation.ReservationStatus;
import com.cielo.flashbooking.reservation.application.ReservationDetails;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReservationEmailServiceTest {

    private final NotificationDeliveryStore notificationDeliveryStore = mock(NotificationDeliveryStore.class);
    private final ReservationReader reservationReader = mock(ReservationReader.class);
    private final ReservationEmailSender reservationEmailSender = mock(ReservationEmailSender.class);

    @Test
    void sendsTemporaryReservationDetailsAndAcknowledgesTheMessage() {
        UUID outboxEventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        when(notificationDeliveryStore.lockOrCreate(outboxEventId))
                .thenReturn(new NotificationDelivery(outboxEventId, NotificationDeliveryStatus.PENDING, 0));
        when(notificationDeliveryStore.recordAttempt(outboxEventId)).thenReturn(1);
        when(reservationReader.findById(reservationId)).thenReturn(Optional.of(reservation(reservationId)));
        when(reservationEmailSender.send(any())).thenReturn("provider-message-id");

        ReservationEmailService.ProcessingResult result = service(3).process(outboxEventId, reservationId);

        assertThat(result.acknowledged()).isTrue();
        ArgumentCaptor<ReservationEmail> email = ArgumentCaptor.forClass(ReservationEmail.class);
        verify(reservationEmailSender).send(email.capture());
        assertThat(email.getValue().recipient()).isEqualTo("customer@example.com");
        assertThat(email.getValue().body())
                .contains(reservationId.toString(), "Test event", "Quantidade: 2", "2026-09-09T12:10:00Z")
                .contains("reserva é temporária e não confirma compra nem pagamento");
        verify(notificationDeliveryStore).markSent(outboxEventId, "provider-message-id");
    }

    @Test
    void preservesTheReservationAndLeavesMessageForRetryWhenEmailFails() {
        UUID outboxEventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        when(notificationDeliveryStore.lockOrCreate(outboxEventId))
                .thenReturn(new NotificationDelivery(outboxEventId, NotificationDeliveryStatus.PENDING, 1));
        when(notificationDeliveryStore.recordAttempt(outboxEventId)).thenReturn(2);
        when(reservationReader.findById(reservationId)).thenReturn(Optional.of(reservation(reservationId)));
        when(reservationEmailSender.send(any())).thenThrow(new IllegalStateException("provider unavailable"));

        ReservationEmailService.ProcessingResult result = service(2).process(outboxEventId, reservationId);

        assertThat(result.acknowledged()).isFalse();
        assertThat(result.maskedRecipient()).isEqualTo("c***@example.com");
        verify(notificationDeliveryStore).markFailed(outboxEventId);
        verify(notificationDeliveryStore, never()).markSent(any(), anyString());
    }

    @Test
    void acknowledgesARepeatedMessageAfterItWasAlreadySent() {
        UUID outboxEventId = UUID.randomUUID();
        when(notificationDeliveryStore.lockOrCreate(outboxEventId))
                .thenReturn(new NotificationDelivery(outboxEventId, NotificationDeliveryStatus.SENT, 1));

        ReservationEmailService.ProcessingResult result = service(3).process(outboxEventId, UUID.randomUUID());

        assertThat(result.acknowledged()).isTrue();
        verify(reservationEmailSender, never()).send(any());
        verify(notificationDeliveryStore, never()).recordAttempt(any());
    }

    private ReservationEmailService service(int maximumAttempts) {
        return new ReservationEmailService(
                notificationDeliveryStore,
                reservationReader,
                reservationEmailSender,
                new NotificationConsumerProperties(false, null, maximumAttempts, 2));
    }

    private ReservationDetails reservation(UUID reservationId) {
        return new ReservationDetails(
                reservationId,
                new ReservationDetails.Event(UUID.randomUUID(), "Test event"),
                new ReservationDetails.Customer(UUID.randomUUID(), "Test customer", "customer@example.com"),
                2,
                ReservationStatus.PENDING,
                Instant.parse("2026-09-09T12:10:00Z"),
                null);
    }
}
