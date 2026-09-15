package com.cielo.flashbooking.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyControllerIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
        registry.add("idempotency.cleanup.fixed-delay", () -> "1h");
        registry.add("idempotency.cleanup.initial-delay", () -> "1h");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void clearState() {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void createEvent_whenRequestRepeats_returnsTheStoredResponseAndDoesNotCreateAnotherEvent() throws Exception {
        String key = UUID.randomUUID().toString();
        String request = objectMapper.writeValueAsString(Map.of("name", "Idempotent show", "capacity", 10));

        String first = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(10))
                .andReturn().getResponse().getContentAsString();
        String repeated = mockMvc.perform(post("/events")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(repeated)).isEqualTo(objectMapper.readTree(first));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT response_status FROM idempotency_record WHERE idempotency_key = ?", Integer.class, key))
                .isEqualTo(201);
    }

    @Test
    void createEvent_whenKeyIsReusedWithDifferentPayload_returnsConflict() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/events")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Idempotent show\",\"capacity\":10}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/events")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Idempotent show\",\"capacity\":11}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("resource-conflict"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class)).isEqualTo(1);
    }

    @Test
    void createReservation_whenKeyExpires_allowsOneNewEffectAcrossParallelRetries() throws Exception {
        UUID eventId = insertEvent(10, 10);
        String key = UUID.randomUUID().toString();
        String request = objectMapper.writeValueAsString(Map.of(
                "quantity", 1,
                "customer", Map.of("name", "Ana", "email", "ana@example.com")));
        List<String> initialResponses = performParallelReservationRequests(eventId, key, request);

        assertThat(objectMapper.readTree(initialResponses.get(1)))
                .isEqualTo(objectMapper.readTree(initialResponses.getFirst()));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(9);

        jdbcTemplate.update(
                """
                UPDATE idempotency_record
                SET created_at = clock_timestamp() - interval '25 hours',
                    expires_at = clock_timestamp() - interval '1 second'
                WHERE idempotency_key = ?
                """,
                key);
        String newRequest = objectMapper.writeValueAsString(Map.of(
                "quantity", 1,
                "customer", Map.of("name", "Bia", "email", "bia@example.com")));

        List<String> reclaimedResponses = performParallelReservationRequests(eventId, key, newRequest);

        assertThat(objectMapper.readTree(reclaimedResponses.get(1)))
                .isEqualTo(objectMapper.readTree(reclaimedResponses.getFirst()));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_record", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT expires_at > clock_timestamp() FROM idempotency_record WHERE idempotency_key = ?",
                        Boolean.class,
                        key))
                .isTrue();
    }

    @Test
    void createReservation_whenEventIsMissing_recordsTheFinalNotFoundResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/events/{eventId}/reservations", UUID.randomUUID())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"customer\":{\"name\":\"Ana\",\"email\":\"ana@example.com\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource-not-found"));

        assertThat(jdbcTemplate.queryForObject("SELECT response_status FROM idempotency_record WHERE idempotency_key = ?", Integer.class, key))
                .isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isZero();
    }

    @Test
    void createReservation_whenCapacityIsInsufficient_recordsAndRepeatsTheFinalConflictResponse() throws Exception {
        UUID eventId = insertEvent(1, 1);
        String key = UUID.randomUUID().toString();
        String request = "{\"quantity\":2,\"customer\":{\"name\":\"Ana\",\"email\":\"ana@example.com\"}}";

        String first = mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("resource-conflict"))
                .andReturn().getResponse().getContentAsString();
        String repeated = mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(repeated)).isEqualTo(objectMapper.readTree(first));
        assertThat(jdbcTemplate.queryForObject("SELECT response_status FROM idempotency_record WHERE idempotency_key = ?", Integer.class, key))
                .isEqualTo(409);
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isZero();
    }

    @Test
    void cancel_whenRequestRepeats_returnsTheStoredTerminalResponseWithoutASecondCapacityReturn() throws Exception {
        UUID reservationId = insertPendingReservation();
        UUID eventId = jdbcTemplate.queryForObject("SELECT event_id FROM reservation WHERE id = ?", UUID.class, reservationId);
        String key = UUID.randomUUID().toString();

        String first = mockMvc.perform(delete("/reservations/{id}", reservationId).header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andReturn().getResponse().getContentAsString();
        String repeated = mockMvc.perform(delete("/reservations/{id}", reservationId).header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(repeated)).isEqualTo(objectMapper.readTree(first));
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(10);
    }

    @Test
    void mutableCommand_whenIdempotencyKeyIsMissing_returnsBadRequestWithoutEffect() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing key\",\"capacity\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_record", Integer.class)).isZero();
    }

    @Test
    void createReservation_whenIdempotencyKeyIsMissing_returnsBadRequestWithoutInventoryEffect() throws Exception {
        UUID eventId = insertEvent(10, 10);

        mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"customer\":{\"name\":\"Ana\",\"email\":\"ana@example.com\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));

        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isZero();
    }

    @Test
    void cancel_whenIdempotencyKeyIsMissing_returnsBadRequestWithoutChangingTheReservation() throws Exception {
        UUID reservationId = insertPendingReservation();
        UUID eventId = jdbcTemplate.queryForObject("SELECT event_id FROM reservation WHERE id = ?", UUID.class, reservationId);

        mockMvc.perform(delete("/reservations/{id}", reservationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(7);
    }

    private UUID insertEvent(int capacity, int available) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                id,
                "Idempotency event",
                capacity,
                available,
                java.sql.Timestamp.from(Instant.parse("2026-09-09T12:00:00Z")));
        return id;
    }

    private List<String> performParallelReservationRequests(UUID eventId, String key, String request) throws Exception {
        List<Callable<String>> requests = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            requests.add(() -> mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
        }

        List<String> responses = new ArrayList<>();
        for (Future<String> response : executor.invokeAll(requests)) {
            responses.add(response.get());
        }
        return responses;
    }

    private UUID insertPendingReservation() {
        UUID eventId = insertEvent(10, 7);
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-09T12:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId,
                "Ana",
                "ana@example.com",
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 3, 'PENDING', ?, ?, ?)
                """,
                reservationId,
                eventId,
                customerId,
                java.sql.Timestamp.from(createdAt.plusSeconds(600)),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        return reservationId;
    }
}
