package com.cielo.flashbooking.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
    "management.otlp.metrics.export.enabled=true",
    "management.otlp.metrics.export.url=http://127.0.0.1:1/v1/metrics",
    "management.otlp.metrics.export.step=100ms",
    "management.otlp.metrics.export.connect-timeout=100ms",
    "management.otlp.metrics.export.read-timeout=200ms",
    "management.tracing.export.enabled=true",
    "management.tracing.export.otlp.enabled=true",
    "management.tracing.sampling.probability=1.0",
    "management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:1/v1/traces",
    "management.opentelemetry.tracing.export.otlp.connect-timeout=100ms",
    "management.opentelemetry.tracing.export.otlp.timeout=200ms",
    "management.opentelemetry.tracing.export.schedule-delay=100ms"
})
@AutoConfigureMockMvc
class ObservabilityExportResilienceIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsReservationWhenOtlpCollectorIsUnavailable() throws Exception {
        String eventResponse = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(jsonMapper.writeValueAsString(Map.of("name", "Telemetry test", "capacity", 5))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String eventId = jsonMapper.readTree(eventResponse).get("id").asString();

        mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(jsonMapper.writeValueAsString(Map.of(
                                "quantity", 1,
                                "customer", Map.of("name", "Telemetry User", "email", "telemetry@example.com")))))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?::uuid", Integer.class, eventId))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(1);
    }
}
