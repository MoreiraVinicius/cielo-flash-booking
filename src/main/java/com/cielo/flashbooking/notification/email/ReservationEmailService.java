package com.cielo.flashbooking.notification.email;

import com.cielo.flashbooking.reservation.application.ReservationDetails;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ReservationEmailService {

    private final NotificationDeliveryStore notificationDeliveryStore;
    private final ReservationReader reservationReader;
    private final ReservationEmailSender reservationEmailSender;
    private final NotificationConsumerProperties properties;

    public ReservationEmailService(
            NotificationDeliveryStore notificationDeliveryStore,
            ReservationReader reservationReader,
            ReservationEmailSender reservationEmailSender,
            NotificationConsumerProperties properties) {
        this.notificationDeliveryStore = notificationDeliveryStore;
        this.reservationReader = reservationReader;
        this.reservationEmailSender = reservationEmailSender;
        this.properties = properties;
    }

    @Transactional
    public ProcessingResult process(UUID outboxEventId, UUID reservationId) {
        NotificationDelivery delivery = notificationDeliveryStore.lockOrCreate(outboxEventId);
        if (delivery.status() == NotificationDeliveryStatus.SENT) {
            return ProcessingResult.acknowledgedResult();
        }
        if (delivery.status() == NotificationDeliveryStatus.FAILED) {
            return ProcessingResult.retry("recipient unavailable");
        }

        int attempts = notificationDeliveryStore.recordAttempt(outboxEventId);
        String maskedRecipient = "recipient unavailable";
        try {
            ReservationDetails reservation = reservationReader.findById(reservationId)
                    .orElseThrow(() -> new IllegalArgumentException("reservation was not found"));
            maskedRecipient = maskRecipient(reservation.customer().email());
            ReservationEmail email = ReservationEmail.temporaryReservation(
                    reservation.customer().email(),
                    reservation.id(),
                    reservation.event().name(),
                    reservation.quantity(),
                    reservation.expiresAt());
            String providerMessageId = reservationEmailSender.send(email);
            notificationDeliveryStore.markSent(outboxEventId, providerMessageId);
            return ProcessingResult.acknowledgedResult();
        } catch (Exception exception) {
            if (attempts >= properties.maximumAttempts()) {
                notificationDeliveryStore.markFailed(outboxEventId);
            }
            return ProcessingResult.retry(maskedRecipient);
        }
    }

    private String maskRecipient(String recipient) {
        int separator = recipient.indexOf('@');
        if (separator <= 0 || separator == recipient.length() - 1) {
            return "***";
        }
        return recipient.charAt(0) + "***@" + recipient.substring(separator + 1);
    }

    public record ProcessingResult(boolean acknowledged, String maskedRecipient) {

        static ProcessingResult acknowledgedResult() {
            return new ProcessingResult(true, null);
        }

        static ProcessingResult retry(String maskedRecipient) {
            return new ProcessingResult(false, maskedRecipient);
        }
    }
}
