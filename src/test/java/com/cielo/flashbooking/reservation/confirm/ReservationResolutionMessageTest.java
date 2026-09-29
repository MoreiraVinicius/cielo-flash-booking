package com.cielo.flashbooking.reservation.confirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReservationResolutionMessageTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void parse_whenConfirmationEnvelopeIsValid_mapsItsTypedFields() {
        UUID reservationId = UUID.randomUUID();
        var message = ReservationResolutionMessage.parse(confirmationBody(reservationId), objectMapper);

        assertThat(message.type()).isEqualTo(ReservationResolutionMessage.Type.CONFIRMATION_REQUESTED);
        assertThat(message.source()).isEqualTo("reservation-owner");
        assertThat(message.resolutionId()).isEqualTo("resolution-1");
        assertThat(message.reservationId()).isEqualTo(reservationId);
        assertThat(message.requestedAt()).isEqualTo(Instant.parse("2026-09-29T14:00:00Z"));
        assertThat(message.cancellationId()).isNull();
    }

    @Test
    void parse_whenCancellationCompletionIsValid_mapsItsCorrelationId() {
        UUID reservationId = UUID.randomUUID();
        UUID cancellationId = UUID.randomUUID();
        String body = """
                {"version":1,"type":"ReservationCancellationCompleted","source":"reservation-owner",
                 "resolutionId":"resolution-2","reservationId":"%s","cancellationId":"%s"}
                """.formatted(reservationId, cancellationId);

        var message = ReservationResolutionMessage.parse(body, objectMapper);

        assertThat(message.type()).isEqualTo(ReservationResolutionMessage.Type.CANCELLATION_COMPLETED);
        assertThat(message.reservationId()).isEqualTo(reservationId);
        assertThat(message.cancellationId()).isEqualTo(cancellationId);
        assertThat(message.requestedAt()).isNull();
    }

    @Test
    void payloadFingerprint_whenJsonFormattingChanges_remainsStableForTheSameResolution() {
        UUID reservationId = UUID.randomUUID();
        String compact = confirmationBody(reservationId);
        String reformatted = """
                {
                  "reservationId": "%s",
                  "resolutionId": "resolution-1",
                  "source": "reservation-owner",
                  "type": "ReservationConfirmationRequested",
                  "version": 1,
                  "requestedAt": "2026-09-29T14:00:00Z"
                }
                """.formatted(reservationId);

        var first = ReservationResolutionMessage.parse(compact, objectMapper);
        var redelivery = ReservationResolutionMessage.parse(reformatted, objectMapper);

        assertThat(redelivery.payloadFingerprint()).isEqualTo(first.payloadFingerprint());
    }

    @Test
    void parse_whenEnvelopeIsUnsupportedOrUnbounded_rejectsItBeforeProcessing() {
        UUID reservationId = UUID.randomUUID();
        String valid = confirmationBody(reservationId);

        assertThatThrownBy(() -> ReservationResolutionMessage.parse("[]", objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationResolutionMessage.parse(
                        valid.replace("\"version\":1", "\"version\":2"), objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationResolutionMessage.parse(
                        valid.replace("}", ",\"customerEmail\":\"private@example.com\"}"), objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationResolutionMessage.parse(" ".repeat(16 * 1024 + 1), objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_whenRequiredIdentifiersOrTimestampAreInvalid_rejectsTheMessage() {
        UUID reservationId = UUID.randomUUID();
        String valid = confirmationBody(reservationId);

        assertThatThrownBy(() ->
                        ReservationResolutionMessage.parse(valid.replace("resolution-1", " padded "), objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationResolutionMessage.parse(
                        valid.replace(reservationId.toString(), "not-a-uuid"), objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationResolutionMessage.parse(
                        valid.replace("2026-09-29T14:00:00Z", "yesterday"), objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String confirmationBody(UUID reservationId) {
        return """
                {"version":1,"type":"ReservationConfirmationRequested","source":"reservation-owner",
                 "resolutionId":"resolution-1","reservationId":"%s","requestedAt":"2026-09-29T14:00:00Z"}
                """.formatted(reservationId);
    }
}
