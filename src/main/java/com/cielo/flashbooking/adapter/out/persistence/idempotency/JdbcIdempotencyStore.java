package com.cielo.flashbooking.adapter.out.persistence.idempotency;

import com.cielo.flashbooking.application.idempotency.IdempotencyCommand;
import com.cielo.flashbooking.application.idempotency.IdempotencyResponse;
import com.cielo.flashbooking.application.idempotency.IdempotencyStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcIdempotencyStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean claim(IdempotencyCommand command) {
        return jdbcTemplate.update("""
                INSERT INTO idempotency_record (
                    id, idempotency_key, operation, normalized_target, payload_hash,
                    response_status, response_body, expires_at)
                VALUES (?, ?, ?, ?, ?, 102, '{"state":"IN_PROGRESS"}'::jsonb, now() + interval '24 hours')
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                UUID.randomUUID(),
                command.key(),
                command.operation(),
                command.normalizedTarget(),
                command.payloadHash()) == 1;
    }

    @Override
    public Optional<StoredIdempotencyResponse> findByKey(String key) {
        return jdbcTemplate.query("""
                SELECT operation, normalized_target, payload_hash, response_status, response_body::text
                FROM idempotency_record
                WHERE idempotency_key = ?
                """, resultSet -> resultSet.next()
                ? Optional.of(new StoredIdempotencyResponse(
                        resultSet.getString("operation"),
                        resultSet.getString("normalized_target"),
                        resultSet.getString("payload_hash"),
                        resultSet.getInt("response_status"),
                        resultSet.getString("response_body")))
                : Optional.empty(), key);
    }

    @Override
    public void complete(String key, IdempotencyResponse response) {
        int updated = jdbcTemplate.update("""
                UPDATE idempotency_record
                SET response_status = ?, response_body = ?::jsonb
                WHERE idempotency_key = ?
                """, response.status(), serialize(response.body()), key);
        if (updated != 1) {
            throw new IllegalStateException("could not complete idempotency record");
        }
    }

    private String serialize(Object responseBody) {
        try {
            return objectMapper.writeValueAsString(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize idempotency response", exception);
        }
    }
}
