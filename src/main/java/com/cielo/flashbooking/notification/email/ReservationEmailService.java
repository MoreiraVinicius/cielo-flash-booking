package com.cielo.flashbooking.notification.email;

import com.cielo.flashbooking.reservation.application.ReservationDetails;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReservationEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationEmailService.class);

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

    public ProcessingResult process(UUID outboxEventId, UUID reservationId) {
        var claimed = notificationDeliveryStore.claim(
                outboxEventId, properties.maximumAttempts(), properties.leaseDuration());
        if (claimed.isEmpty()) {
            if (notificationDeliveryStore.findStatus(outboxEventId) == NotificationDeliveryStatus.SENT) {
                return ProcessingResult.acknowledgedResult();
            }
            return ProcessingResult.retry("recipient unavailable");
        }

        int attempts = claimed.get().attempts();
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
            LOGGER.info("notification delivery accepted by provider outboxEventId={}", outboxEventId);
            return ProcessingResult.acknowledgedResult();
        } catch (Exception exception) {
            if (attempts >= properties.maximumAttempts()) {
                notificationDeliveryStore.markFailed(outboxEventId);
            } else {
                notificationDeliveryStore.releaseForRetry(outboxEventId);
            }
            LOGGER.warn("notification delivery failed outboxEventId={} attempt={}", outboxEventId, attempts, exception);
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
