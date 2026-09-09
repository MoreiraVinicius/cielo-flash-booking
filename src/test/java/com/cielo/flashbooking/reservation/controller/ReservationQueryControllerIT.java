package com.cielo.flashbooking.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationQueryControllerIT extends LocalIntegrationInfrastructure {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisProperties redisProperties;

    @BeforeEach
    void clearState() {
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
        redisTemplate.delete(redisTemplate.keys("reservation:*"));
    }

    @Test
    void get_whenReservationExists_returnsDetailsAndCacheHitAvoidsPostgresql() throws Exception {
        UUID reservationId = insertReservation("PENDING");

        mockMvc.perform(get("/reservations/{id}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.event.name").value("Reservation event"))
                .andExpect(jsonPath("$.event.available").value(7))
                .andExpect(jsonPath("$.customer.name").value("Ana"))
                .andExpect(jsonPath("$.customer.email").value("ana@example.com"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.closureReason").doesNotExist());

        Long ttlMillis = redisTemplate.getExpire("reservation:" + reservationId, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(ttlMillis).isNotNull().isPositive().isLessThanOrEqualTo(1_000L);

        jdbcTemplate.update("DELETE FROM reservation WHERE id = ?", reservationId);
        mockMvc.perform(get("/reservations/{id}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.email").value("ana@example.com"));
    }

    @Test
    void get_whenTerminalReservationExists_returnsStoredClosureReason() throws Exception {
        UUID reservationId = insertReservation("CANCELLED");

        mockMvc.perform(get("/reservations/{id}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.closureReason.code").value("CANCELLED_BY_REQUEST"))
                .andExpect(jsonPath("$.closureReason.description").value("Reserva cancelada por solicitação"));
    }

    @Test
    void get_whenReservationIsAbsent_returnsNotFound() throws Exception {
        mockMvc.perform(get("/reservations/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource-not-found"));
    }

    @Test
    void get_whenValkeyIsUnavailable_fallsBackToPostgresqlWithinConfiguredTimeout() throws Exception {
        UUID reservationId = insertReservation("PENDING");
        assertThat(redisProperties.getTimeout()).isEqualTo(Duration.ofMillis(100));
        String containerId = VALKEY.getContainerId();
        VALKEY.getDockerClient().pauseContainerCmd(containerId).exec();

        long startedAt = System.nanoTime();
        try {
            mockMvc.perform(get("/reservations/{id}", reservationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reservationId.toString()));
        } finally {
            VALKEY.getDockerClient().unpauseContainerCmd(containerId).exec();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    private UUID insertReservation(String status) {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-09T12:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                eventId,
                "Reservation event",
                10,
                7,
                java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId,
                "Ana",
                "ana@example.com",
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        if ("CANCELLED".equals(status)) {
            jdbcTemplate.update("""
                    INSERT INTO reservation (
                        id, event_id, customer_id, quantity, status, expires_at,
                        closure_reason_code, closure_reason_description, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    reservationId,
                    eventId,
                    customerId,
                    3,
                    status,
                    java.sql.Timestamp.from(createdAt.plus(Duration.ofMinutes(10))),
                    "CANCELLED_BY_REQUEST",
                    "Reserva cancelada por solicitação",
                    java.sql.Timestamp.from(createdAt),
                    java.sql.Timestamp.from(createdAt));
        } else {
            jdbcTemplate.update("""
                    INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    reservationId,
                    eventId,
                    customerId,
                    3,
                    status,
                    java.sql.Timestamp.from(createdAt.plus(Duration.ofMinutes(10))),
                    java.sql.Timestamp.from(createdAt),
                    java.sql.Timestamp.from(createdAt));
        }
        return reservationId;
    }
}
