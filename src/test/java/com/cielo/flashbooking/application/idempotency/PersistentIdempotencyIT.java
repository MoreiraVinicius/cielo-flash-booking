package com.cielo.flashbooking.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class PersistentIdempotencyIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
        registry.add("idempotency.cleanup.batch-size", () -> 2);
        registry.add("idempotency.cleanup.fixed-delay", () -> "1h");
        registry.add("idempotency.cleanup.initial-delay", () -> "1h");
    }

    @Autowired
    private PersistentIdempotencyService idempotencyService;

    @Autowired
    private IdempotencyRecordCleaner idempotencyRecordCleaner;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearRecords() {
        jdbcTemplate.update("DELETE FROM idempotency_record");
    }

    @Test
    void execute_whenUnexpectedFailureOccurs_rollsBackTheClaim() {
        IdempotencyCommand command = IdempotencyCommand.from(
                "failed-command", "POST", "/events", Map.of("capacity", 10), objectMapper);

        assertThatThrownBy(() -> idempotencyService.execute(
                command,
                () -> {
                    throw new IllegalStateException("database unavailable");
                },
                exception -> new IdempotencyResponse(400, Map.of("code", "invalid-request"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_record", Integer.class)).isZero();
    }

    @Test
    void cleanupExpiredRecords_deletesOnlyExpiredRowsInBoundedBatches() {
        insertIdempotencyRecord("expired-1", "-26 hours", "-2 hours");
        insertIdempotencyRecord("expired-2", "-25 hours", "-1 hour");
        insertIdempotencyRecord("expired-3", "-24 hours", "-1 second");
        insertIdempotencyRecord("active", "-1 hour", "+23 hours");

        assertThat(idempotencyRecordCleaner.deleteExpiredRecords()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM idempotency_record WHERE expires_at <= clock_timestamp()",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM idempotency_record WHERE idempotency_key = 'active'",
                        Integer.class))
                .isEqualTo(1);

        assertThat(idempotencyRecordCleaner.deleteExpiredRecords()).isEqualTo(1);
        assertThat(idempotencyRecordCleaner.deleteExpiredRecords()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_record", Integer.class)).isEqualTo(1);
    }

    private void insertIdempotencyRecord(String key, String createdOffset, String expiresOffset) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_record (
                    id, idempotency_key, operation, normalized_target, payload_hash,
                    response_status, response_body, created_at, expires_at)
                VALUES (?, ?, 'POST', '/events', 'payload-hash', 201, '{}'::jsonb,
                    clock_timestamp() + ?::interval, clock_timestamp() + ?::interval)
                """, UUID.randomUUID(), key, createdOffset, expiresOffset);
    }
}
