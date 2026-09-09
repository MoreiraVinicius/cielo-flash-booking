package com.cielo.flashbooking.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
    }

    @Autowired
    private PersistentIdempotencyService idempotencyService;

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
}
