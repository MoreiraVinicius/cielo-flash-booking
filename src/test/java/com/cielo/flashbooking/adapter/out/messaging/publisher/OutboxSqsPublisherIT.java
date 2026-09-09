package com.cielo.flashbooking.adapter.out.messaging.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.adapter.out.persistence.outbox.JdbcOutboxEventStore;
import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@SpringBootTest
class OutboxSqsPublisherIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private SqsClient sqsClient;
    private String expirationQueueUrl;
    private String notificationQueueUrl;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        sqsClient = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();
        expirationQueueUrl = sqsClient.createQueue(request -> request.queueName("expiration-" + UUID.randomUUID())).queueUrl();
        notificationQueueUrl = sqsClient.createQueue(request -> request.queueName("notification-" + UUID.randomUUID())).queueUrl();
    }

    @AfterEach
    void closeClient() {
        sqsClient.close();
    }

    @Test
    void publishesEachOutboxTypeToItsDedicatedQueueOnlyOnce() throws Exception {
        UUID notificationEvent = insertOutboxEvent("ReservationCreated", Instant.parse("2026-09-09T12:10:00Z"));
        UUID expirationEvent = insertOutboxEvent("ReservationExpirationScheduled", Instant.parse("2026-09-09T11:59:00Z"));

        publisher(expirationQueueUrl).publishPendingEvents();

        var notificationMessages = sqsClient.receiveMessage(request -> request.queueUrl(notificationQueueUrl)
                        .messageAttributeNames("All"))
                .messages();
        var expirationMessages = sqsClient.receiveMessage(request -> request.queueUrl(expirationQueueUrl)
                        .messageAttributeNames("All"))
                .messages();
        assertThat(notificationMessages).hasSize(1);
        assertThat(expirationMessages).hasSize(1);
        var notificationMessage = notificationMessages.getFirst();
        var expirationMessage = expirationMessages.getFirst();
        assertThat(notificationMessage.messageAttributes().get("eventType").stringValue()).isEqualTo("ReservationCreated");
        assertThat(expirationMessage.messageAttributes().get("eventType").stringValue()).isEqualTo("ReservationExpirationScheduled");
        assertThat(objectMapper.readTree(notificationMessage.body()))
                .isEqualTo(objectMapper.readTree(payloadOf(notificationEvent)));
        assertThat(objectMapper.readTree(expirationMessage.body()))
                .isEqualTo(objectMapper.readTree(payloadOf(expirationEvent)));
        assertThat(attempts(notificationEvent)).isEqualTo(1);
        assertThat(attempts(expirationEvent)).isEqualTo(1);
        assertThat(published(notificationEvent)).isNotNull();
        assertThat(published(expirationEvent)).isNotNull();

        sqsClient.deleteMessage(request -> request.queueUrl(notificationQueueUrl).receiptHandle(notificationMessage.receiptHandle()));
        sqsClient.deleteMessage(request -> request.queueUrl(expirationQueueUrl).receiptHandle(expirationMessage.receiptHandle()));
        publisher(expirationQueueUrl).publishPendingEvents();

        assertThat(sqsClient.receiveMessage(request -> request.queueUrl(notificationQueueUrl)).messages()).isEmpty();
        assertThat(sqsClient.receiveMessage(request -> request.queueUrl(expirationQueueUrl)).messages()).isEmpty();
    }

    @Test
    void limitsExpirationDelayToFifteenMinutes() {
        UUID eventId = insertOutboxEvent("ReservationExpirationScheduled", clock.instant().plusSeconds(1_200));

        publisher(expirationQueueUrl).publishPendingEvents();

        assertThat(sqsClient.receiveMessage(request -> request.queueUrl(expirationQueueUrl)).messages()).isEmpty();
        assertThat(sqsClient.getQueueAttributes(request -> request.queueUrl(expirationQueueUrl)
                        .attributeNamesWithStrings("ApproximateNumberOfMessagesDelayed"))
                .attributesAsStrings()
                .get("ApproximateNumberOfMessagesDelayed"))
                .isEqualTo("1");
        assertThat(published(eventId)).isNotNull();
    }

    @Test
    void leavesFailedPublicationPendingForARepeatAttempt() {
        UUID eventId = insertOutboxEvent("ReservationExpirationScheduled", Instant.parse("2026-09-09T11:59:00Z"));

        publisher(expirationQueueUrl + "-missing").publishPendingEvents();

        assertThat(published(eventId)).isNull();
        assertThat(attempts(eventId)).isEqualTo(1);

        publisher(expirationQueueUrl).publishPendingEvents();

        assertThat(published(eventId)).isNotNull();
        assertThat(attempts(eventId)).isEqualTo(2);
        assertThat(sqsClient.receiveMessage(request -> request.queueUrl(expirationQueueUrl)).messages()).hasSize(1);
    }

    private OutboxSqsPublisher publisher(String expirationUrl) {
        return new OutboxSqsPublisher(
                new JdbcOutboxEventStore(jdbcTemplate),
                sqsClient,
                expirationUrl,
                notificationQueueUrl,
                clock);
    }

    private UUID insertOutboxEvent(String eventType, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                """,
                id,
                "Reservation",
                UUID.randomUUID(),
                eventType,
                payload(expiresAt),
                java.sql.Timestamp.from(clock.instant()));
        return id;
    }

    private String payload(Instant expiresAt) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "reservationId", UUID.randomUUID().toString(),
                    "eventId", UUID.randomUUID().toString(),
                    "customerId", UUID.randomUUID().toString(),
                    "quantity", 2,
                    "expiresAt", expiresAt.toString()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Integer attempts(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT attempts FROM outbox_event WHERE id = ?", Integer.class, eventId);
    }

    private String payloadOf(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT payload::text FROM outbox_event WHERE id = ?", String.class, eventId);
    }

    private Instant published(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT published_at FROM outbox_event WHERE id = ?", Instant.class, eventId);
    }
}
