package com.cielo.flashbooking.feature.reservation.expire;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@SpringBootTest
class SqsExpirationConsumerIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private ExpireReservationService expireReservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private SqsClient sqsClient;
    private String queueUrl;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
        redisTemplate.delete(redisTemplate.keys("event-availability:*"));
        sqsClient = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();
        queueUrl = sqsClient.createQueue(request -> request.queueName("expiration-" + UUID.randomUUID())).queueUrl();
    }

    @AfterEach
    void closeClient() {
        sqsClient.close();
    }

    @Test
    void poll_whenReservationIsDue_expiresItReturnsCapacityAndInvalidatesCachesBeforeTheDeadline() {
        UUID eventId = insertEvent(10, 7);
        Instant expiresAt = databaseNow().minusMillis(10);
        UUID reservationId = insertPendingReservation(eventId, 3, expiresAt);
        redisTemplate.opsForValue().set("event-availability:" + eventId, "stale");
        send(reservationId);

        consumer().poll();

        assertThat(databaseNow()).isBefore(expiresAt.plusSeconds(5));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT closure_reason_code FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("RESERVATION_DEADLINE_REACHED");
        assertThat(jdbcTemplate.queryForObject("SELECT closure_reason_description FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("Prazo da reserva encerrado");
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId)).isEqualTo(10);
        assertThat(redisTemplate.hasKey("event-availability:" + eventId)).isFalse();
        assertThat(sqsClient.receiveMessage(request -> request.queueUrl(queueUrl)).messages()).isEmpty();
    }

    @Test
    void poll_whenMessageIsEarly_leavesThePendingReservationAndItsCapacityUntouched() {
        UUID eventId = insertEvent(10, 7);
        UUID reservationId = insertPendingReservation(eventId, 3, databaseNow().plusSeconds(60));
        send(reservationId);

        consumer().poll();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT closure_reason_code FROM reservation WHERE id = ?", String.class, reservationId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId)).isEqualTo(7);
    }

    @Test
    void poll_whenExpirationMessageIsDuplicated_returnsCapacityOnlyOnce() {
        UUID eventId = insertEvent(10, 7);
        UUID reservationId = insertPendingReservation(eventId, 3, databaseNow().minusMillis(10));
        send(reservationId);
        send(reservationId);

        consumer().poll();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                .isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId)).isEqualTo(10);
    }

    @Test
    void expire_whenDuplicateMessagesAreHandledConcurrently_returnsCapacityOnlyOnce() throws Exception {
        UUID eventId = insertEvent(10, 7);
        UUID reservationId = insertPendingReservation(eventId, 3, databaseNow().minusMillis(10));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> requests = List.of(
                    () -> expireReservationService.expire(reservationId),
                    () -> expireReservationService.expire(reservationId));

            var results = executor.invokeAll(requests);

            long expired = 0;
            for (var result : results) {
                if (result.get()) {
                    expired++;
                }
            }
            assertThat(expired).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId))
                    .isEqualTo("EXPIRED");
            assertThat(jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId)).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    private SqsExpirationConsumer consumer() {
        return new SqsExpirationConsumer(sqsClient, expireReservationService, queueUrl);
    }

    private void send(UUID reservationId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("reservationId", reservationId.toString()));
            sqsClient.sendMessage(request -> request.queueUrl(queueUrl).messageBody(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID insertEvent(int capacity, int available) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                eventId,
                "Expiration event",
                capacity,
                available,
                java.sql.Timestamp.from(Instant.now()));
        return eventId;
    }

    private Instant databaseNow() {
        return jdbcTemplate.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class).toInstant();
    }

    private UUID insertPendingReservation(UUID eventId, int quantity, Instant expiresAt) {
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = expiresAt.minusSeconds(600);
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId,
                "Expiration customer",
                customerId + "@example.com",
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """,
                reservationId,
                eventId,
                customerId,
                quantity,
                java.sql.Timestamp.from(expiresAt),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        return reservationId;
    }
}
