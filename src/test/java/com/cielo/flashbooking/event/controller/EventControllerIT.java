package com.cielo.flashbooking.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventControllerIT extends LocalIntegrationInfrastructure {

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

    @Autowired
    private RedisProperties redisProperties;

    @BeforeEach
    void clearEvents() {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM event");
        redisTemplate.delete(redisTemplate.keys("event-availability:*"));
    }

    @Test
    @Order(1)
    void createsAValidEventAndPersistsIt() throws Exception {
        String response = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(Map.of("name", " Arena show ", "capacity", 120))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/events/[0-9a-f-]{36}")))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Arena show"))
                .andExpect(jsonPath("$.capacity").value(120))
                .andExpect(jsonPath("$.available").value(120))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();
        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "SELECT name, capacity, available FROM event WHERE id = ?::uuid", id);
        assertThat(persisted)
                .containsEntry("name", "Arena show")
                .containsEntry("capacity", 120)
                .containsEntry("available", 120);
    }

    @ParameterizedTest
    @Order(2)
    @MethodSource("invalidEvents")
    void rejectsInvalidEventsWithoutPersistence(String name, int capacity) throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "capacity", capacity))))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.code").value("invalid-request"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event", Integer.class)).isZero();
    }

    private static Stream<Arguments> invalidEvents() {
        return Stream.of(
                Arguments.of("", 10),
                Arguments.of("x".repeat(201), 10),
                Arguments.of("valid", 0),
                Arguments.of("valid", -1));
    }

    @Test
    @Order(3)
    void returnsAndCachesAvailabilityWithoutASecondPostgresqlQuery() throws Exception {
        UUID id = insertEvent(100, 42);

        mockMvc.perform(get("/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Cached event"))
                .andExpect(jsonPath("$.capacity").value(100))
                .andExpect(jsonPath("$.available").value(42));

        Long ttlMillis = redisTemplate.getExpire("event-availability:" + id, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(ttlMillis).isNotNull().isPositive().isLessThanOrEqualTo(1_000L);

        jdbcTemplate.update("DELETE FROM event WHERE id = ?", id);
        mockMvc.perform(get("/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(42));
    }

    @Test
    @Order(4)
    void returnsNotFoundForUnknownEvent() throws Exception {
        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource-not-found"));
    }

    @Test
    @Order(5)
    void fallsBackToPostgresqlWhenValkeyIsUnavailableWithConfiguredTimeout() throws Exception {
        UUID id = insertEvent(30, 12);
        assertThat(redisProperties.getTimeout()).isEqualTo(Duration.ofMillis(100));
        String containerId = VALKEY.getContainerId();
        VALKEY.getDockerClient().pauseContainerCmd(containerId).exec();

        long startedAt = System.nanoTime();
        try {
            mockMvc.perform(get("/events/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(12));
        } finally {
            VALKEY.getDockerClient().unpauseContainerCmd(containerId).exec();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    private UUID insertEvent(int capacity, int available) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                id,
                "Cached event",
                capacity,
                available,
                java.sql.Timestamp.from(Instant.parse("2026-09-09T12:00:00Z")));
        return id;
    }
}
