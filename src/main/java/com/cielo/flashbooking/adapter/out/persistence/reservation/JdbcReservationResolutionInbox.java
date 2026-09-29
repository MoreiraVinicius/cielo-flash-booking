package com.cielo.flashbooking.adapter.out.persistence.reservation;

import com.cielo.flashbooking.reservation.confirm.ReservationResolutionInbox;
import com.cielo.flashbooking.reservation.confirm.ReservationResolutionMessage;
import com.cielo.flashbooking.reservation.confirm.ReservationResolutionOutcome;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcReservationResolutionInbox implements ReservationResolutionInbox {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper objectMapper;

    JdbcReservationResolutionInbox(JdbcTemplate jdbcTemplate, JsonMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean tryBegin(ReservationResolutionMessage message, String payloadFingerprint) {
        return jdbcTemplate.update(
                        """
                        INSERT INTO confirmation_inbox (
                            source, resolution_id, reservation_id, message_type, payload_fingerprint, outcome, cancellation_id
                        ) VALUES (?, ?, (SELECT id FROM reservation WHERE id = ?), ?, ?, jsonb_build_object('code', 'PROCESSING'), ?::uuid)
                        ON CONFLICT (source, resolution_id) DO NOTHING
                        """,
                        message.source(),
                        message.resolutionId(),
                        message.reservationId(),
                        message.type().wireValue(),
                        payloadFingerprint,
                        message.cancellationId())
                == 1;
    }

    @Override
    public Optional<StoredResolution> find(String source, String resolutionId) {
        List<StoredResolution> rows = jdbcTemplate.query(
                """
                SELECT payload_fingerprint, outcome
                FROM confirmation_inbox
                WHERE source = ? AND resolution_id = ?
                """,
                (resultSet, rowNumber) -> new StoredResolution(
                        resultSet.getString("payload_fingerprint"), deserializeOutcome(resultSet.getString("outcome"))),
                source,
                resolutionId);
        return rows.stream().findFirst();
    }

    @Override
    public void complete(String source, String resolutionId, ReservationResolutionOutcome outcome) {
        try {
            String serializedOutcome = objectMapper.writeValueAsString(outcome);
            int updated = jdbcTemplate.update("""
                    UPDATE confirmation_inbox
                    SET outcome = ?::jsonb, processed_at = clock_timestamp()
                    WHERE source = ? AND resolution_id = ?
                    """, serializedOutcome, source, resolutionId);
            if (updated != 1) {
                throw new IllegalStateException("reservation resolution inbox row disappeared before completion");
            }
        } catch (JacksonException exception) {
            throw new IllegalStateException("could not serialize reservation resolution outcome", exception);
        }
    }

    private ReservationResolutionOutcome deserializeOutcome(String value) {
        try {
            return objectMapper.readValue(value, ReservationResolutionOutcome.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("could not deserialize reservation resolution outcome", exception);
        }
    }
}
