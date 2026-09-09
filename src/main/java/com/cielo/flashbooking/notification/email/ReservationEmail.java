package com.cielo.flashbooking.notification.email;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

public record ReservationEmail(String recipient, String subject, String body) {

    public ReservationEmail {
        Objects.requireNonNull(recipient, "recipient must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
    }

    static ReservationEmail temporaryReservation(
            String recipient,
            UUID reservationId,
            String eventName,
            int quantity,
            Instant expiresAt) {
        String body = """
                Sua reserva temporária foi registrada.

                Reserva: %s
                Evento: %s
                Quantidade: %d
                Válida até: %s

                Esta reserva é temporária e não confirma compra nem pagamento.
                """.formatted(
                reservationId,
                eventName,
                quantity,
                DateTimeFormatter.ISO_INSTANT.format(expiresAt));
        return new ReservationEmail(recipient, "Reserva temporária registrada", body);
    }
}
