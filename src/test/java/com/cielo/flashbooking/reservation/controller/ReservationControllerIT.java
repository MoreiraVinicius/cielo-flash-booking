package com.cielo.flashbooking.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationControllerIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearState() {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
        redisTemplate.delete(redisTemplate.keys("event-availability:*"));
    }

    @Test
    void create_whenRequestIsValid_commitsAllRecordsAndInvalidatesAvailabilityCache() throws Exception {
        UUID eventId = insertEvent(10, 10);
        redisTemplate.opsForValue().set("event-availability:" + eventId, "stale");

        String response = mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(Map.of(
                                "quantity", 3,
                                "customer", Map.of("name", " Ana ", "email", "ANA@EXAMPLE.COM")))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/reservations/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.customer.name").value("Ana"))
                .andExpect(jsonPath("$.customer.email").value("ana@example.com"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.closureReason").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID reservationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation WHERE id = ?", Integer.class, reservationId))
                .isEqualTo(1);
        List<Map<String, Object>> outboxEvents = jdbcTemplate.queryForList(
                "SELECT event_type, payload FROM outbox_event WHERE aggregate_id = ? ORDER BY event_type", reservationId);
        assertThat(outboxEvents).hasSize(2);
        assertThat(outboxEvents)
                .extracting(event -> event.get("event_type"))
                .containsExactly("ReservationCreated", "ReservationExpirationScheduled");
        assertThat(outboxEvents)
                .allSatisfy(event -> assertThat(event.get("payload").toString())
                        .contains(reservationId.toString(), eventId.toString(), "expiresAt"));
        assertThat(redisTemplate.hasKey("event-availability:" + eventId)).isFalse();
    }

    @Test
    void create_whenCustomerIsInvalid_returnsBadRequestWithoutAnyEffect() throws Exception {
        UUID eventId = insertEvent(10, 10);

        mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"quantity": 1, "customer": {"name": "", "email": "not-an-email"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));

        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
    }

    @Test
    void create_whenCustomerEmailHasOuterWhitespace_normalizesBeforeValidationAndPersistence() throws Exception {
        UUID eventId = insertEvent(10, 10);

        mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"quantity": 1, "customer": {"name": " Ana ", "email": " ANA@EXAMPLE.COM "}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.name").value("Ana"))
                .andExpect(jsonPath("$.customer.email").value("ana@example.com"));

        assertThat(jdbcTemplate.queryForObject("SELECT email FROM customer", String.class)).isEqualTo("ana@example.com");
    }

    @Test
    void create_whenCustomerEmailAlreadyExists_preservesPersistedIdentity() throws Exception {
        UUID firstEventId = insertEvent(10, 10);
        UUID secondEventId = insertEvent(10, 10);

        mockMvc.perform(post("/events/{eventId}/reservations", firstEventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"quantity": 1, "customer": {"name": "Ana Original", "email": "ana@example.com"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.name").value("Ana Original"));

        mockMvc.perform(post("/events/{eventId}/reservations", secondEventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"quantity": 1, "customer": {"name": "Nome Diferente", "email": "ana@example.com"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.name").value("Ana Original"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM customer WHERE email = 'ana@example.com'", String.class))
                .isEqualTo("Ana Original");
    }

    @Test
    void create_whenEventIsAbsent_returnsNotFoundWithoutAnyEffect() throws Exception {
        mockMvc.perform(post("/events/{eventId}/reservations", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(validRequest(1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource-not-found"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
    }

    @Test
    void create_whenCapacityIsInsufficient_returnsConflictWithoutPartialEffects() throws Exception {
        UUID eventId = insertEvent(2, 2);

        mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(validRequest(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("resource-conflict"));

        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
    }

    @Test
    void create_whenSaleHasNotStarted_replaysConflictWithoutPartialEffects() throws Exception {
        UUID eventId = insertEvent(10, 10);
        String key = UUID.randomUUID().toString();
        jdbcTemplate.update("UPDATE event SET starts_at = clock_timestamp() + interval '10 minutes' WHERE id = ?", eventId);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", key)
                            .content(validRequest(1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("resource-conflict"));
        }

        assertWindowRejectionHasNoReservationEffects(eventId, 10);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_record", Integer.class)).isEqualTo(1);
    }

    @Test
    void create_whenSaleHasEnded_returnsConflictWithoutPartialEffects() throws Exception {
        UUID eventId = insertEvent(10, 10);
        jdbcTemplate.update("UPDATE event SET ends_at = clock_timestamp() WHERE id = ?", eventId);

        mockMvc.perform(post("/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(validRequest(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("resource-conflict"));

        assertWindowRejectionHasNoReservationEffects(eventId, 10);
    }

    private String validRequest(int quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "quantity", quantity,
                "customer", Map.of("name", "Ana", "email", "ana@example.com")));
    }

    private void assertWindowRejectionHasNoReservationEffects(UUID eventId, int expectedAvailable) {
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId))
                .isEqualTo(expectedAvailable);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isZero();
    }

    private UUID insertEvent(int capacity, int available) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                id,
                "Reservation event",
                capacity,
                available,
                java.sql.Timestamp.from(Instant.parse("2026-09-09T12:00:00Z")));
        return id;
    }
}
