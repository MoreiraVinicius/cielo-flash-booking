package com.cielo.flashbooking.reservation.confirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cielo.flashbooking.adapter.out.messaging.publisher.OutboxSqsPublisher;
import com.cielo.flashbooking.application.outbox.OutboxEventStore;
import com.cielo.flashbooking.reservation.application.CancelReservationService;
import com.cielo.flashbooking.reservation.application.CreateReservationService;
import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
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
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class SqsReservationConfirmationConsumerIT extends LocalIntegrationInfrastructure {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
        registry.add("confirmation.consumer.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private ReservationResolutionProcessor processor;

    @Autowired
    private OutboxEventStore outboxEventStore;

    @Autowired
    private CreateReservationService createReservationService;

    @Autowired
    private CancelReservationService cancelReservationService;

    private SqsClient sqsClient;
    private String queueUrl;
    private String deadLetterQueueUrl;
    private String ownerQueueUrl;
    private String expirationQueueUrl;
    private String notificationQueueUrl;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM confirmation_inbox");
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
        deadLetterQueueUrl = createQueue("resolution-dlq-" + UUID.randomUUID(), Map.of());
        queueUrl = createQueue("resolution-" + UUID.randomUUID(), sourceQueueAttributes(5, 0));
        ownerQueueUrl = createQueue("reservation-owner-" + UUID.randomUUID(), Map.of());
        expirationQueueUrl = createQueue("reservation-expiration-" + UUID.randomUUID(), Map.of());
        notificationQueueUrl = createQueue("reservation-notification-" + UUID.randomUUID(), Map.of());
    }

    private String lastCreatedQueueUrl;

    @AfterEach
    void cleanUp() {
        if (sqsClient != null) {
            if (queueUrl != null) {
                sqsClient.deleteQueue(request -> request.queueUrl(queueUrl));
            }
            if (deadLetterQueueUrl != null) {
                sqsClient.deleteQueue(request -> request.queueUrl(deadLetterQueueUrl));
            }
            if (ownerQueueUrl != null) {
                sqsClient.deleteQueue(request -> request.queueUrl(ownerQueueUrl));
            }
            if (expirationQueueUrl != null) {
                sqsClient.deleteQueue(request -> request.queueUrl(expirationQueueUrl));
            }
            if (notificationQueueUrl != null) {
                sqsClient.deleteQueue(request -> request.queueUrl(notificationQueueUrl));
            }
            sqsClient.close();
        }
    }

    @Test
    void poll_whenSameResolutionIsRedeliveredWithDifferentSqsMessageIds_confirmsAndCommitsOnce() {
        Fixture fixture = insertPendingReservation(10, 7, 3);
        String body = confirmationBody(fixture.reservationId(), "resolution-1");
        String firstMessageId = send(body);
        String redeliveryMessageId = send(body);
        assertThat(redeliveryMessageId).isNotEqualTo(firstMessageId);

        consumer().poll();

        assertThat(reservationStatus(fixture.reservationId())).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT confirmed_at FROM reservation WHERE id = ?", Timestamp.class, fixture.reservationId()))
                .isNotNull();
        assertThat(available(fixture.eventId())).isEqualTo(7);
        assertThat(inboxCount("reservation-owner", "resolution-1")).isEqualTo(1);
        assertThat(inboxOutcome("reservation-owner", "resolution-1")).isEqualTo("CONFIRMED");
        assertThat(outboxCount("ReservationConfirmed", fixture.reservationId())).isEqualTo(1);
        assertThat(outboxCount("ReservationConfirmationRejected", fixture.reservationId()))
                .isZero();
        awaitSourceQueueEmpty();
    }

    @Test
    void process_whenSameResolutionIsConcurrent_commitsOnlyOneConfirmation() throws Exception {
        Fixture fixture = insertPendingReservation(10, 7, 3);
        ReservationResolutionMessage message = ReservationResolutionMessage.parse(
                confirmationBody(fixture.reservationId(), "resolution-concurrent"), objectMapper);
        String fingerprint = message.payloadFingerprint();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> processor.process(message, fingerprint));
            var second = executor.submit(() -> processor.process(message, fingerprint));

            assertThat(first.get()).isEqualTo(second.get());
            assertThat(reservationStatus(fixture.reservationId())).isEqualTo("CONFIRMED");
            assertThat(available(fixture.eventId())).isEqualTo(7);
            assertThat(inboxCount("reservation-owner", "resolution-concurrent")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void process_whenDistinctResolutionsRaceForSameReservation_commitsCapacityOnlyOnce() throws Exception {
        Fixture fixture = insertPendingReservation(10, 7, 3);
        ReservationResolutionMessage firstMessage = ReservationResolutionMessage.parse(
                confirmationBody(fixture.reservationId(), "resolution-distinct-1"), objectMapper);
        ReservationResolutionMessage secondMessage = ReservationResolutionMessage.parse(
                confirmationBody(fixture.reservationId(), "resolution-distinct-2"), objectMapper);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> processor.process(firstMessage, firstMessage.payloadFingerprint()));
            var second = executor.submit(() -> processor.process(secondMessage, secondMessage.payloadFingerprint()));

            assertThat(java.util.Set.of(
                            first.get(5, java.util.concurrent.TimeUnit.SECONDS).code(),
                            second.get(5, java.util.concurrent.TimeUnit.SECONDS).code()))
                    .containsExactlyInAnyOrder("CONFIRMED", "ALREADY_CONFIRMED");
            assertThat(reservationStatus(fixture.reservationId())).isEqualTo("CONFIRMED");
            assertThat(available(fixture.eventId())).isEqualTo(7);
            assertThat(inboxCount("reservation-owner", "resolution-distinct-1")).isEqualTo(1);
            assertThat(inboxCount("reservation-owner", "resolution-distinct-2")).isEqualTo(1);
            assertThat(outboxCount("ReservationConfirmed", fixture.reservationId()))
                    .isEqualTo(2);
            assertThat(outboxCount("ReservationConfirmationRejected", fixture.reservationId()))
                    .isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void poll_whenCancellationWonBeforeConfirmation_rejectsWithoutReturningCapacityAgain() {
        Fixture fixture = insertPendingReservation(10, 7, 3);
        cancelReservationService.cancel(fixture.reservationId());
        send(confirmationBody(fixture.reservationId(), "resolution-after-cancel"));

        consumer().poll();

        assertThat(reservationStatus(fixture.reservationId())).isEqualTo("CANCELLED");
        assertThat(available(fixture.eventId())).isEqualTo(10);
        assertThat(inboxOutcome("reservation-owner", "resolution-after-cancel")).isEqualTo("CANCELLED");
        assertThat(outboxCount("ReservationConfirmationRejected", fixture.reservationId()))
                .isEqualTo(1);
        assertThat(outboxCount("ReservationConfirmed", fixture.reservationId())).isZero();
        assertThat(outboxCount("ReservationHoldClosed", fixture.reservationId()))
                .isEqualTo(1);
    }

    @Test
    void poll_whenEventSaleEndedButReservationDeadlineIsFuture_confirmsAndKeepsCapacityCommitted() {
        Fixture fixture = insertPendingReservation(10, 7, 3);
        jdbcTemplate.update(
                "UPDATE event SET ends_at = clock_timestamp() - INTERVAL '30 seconds' WHERE id = ?", fixture.eventId());

        send(confirmationBody(fixture.reservationId(), "resolution-after-event-end"));
        consumer().poll();

        assertThat(reservationStatus(fixture.reservationId())).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT expires_at > clock_timestamp() FROM reservation WHERE id = ?",
                        Boolean.class,
                        fixture.reservationId()))
                .isTrue();
        assertThat(available(fixture.eventId())).isEqualTo(7);
        assertThat(inboxOutcome("reservation-owner", "resolution-after-event-end"))
                .isEqualTo("CONFIRMED");
        assertThat(outboxCount("ReservationConfirmed", fixture.reservationId())).isEqualTo(1);
    }

    @Test
    void poll_whenResolutionIdentityHasDifferentPayload_leavesConflictForRetryAndDeadLetter() {
        Fixture first = insertPendingReservation(10, 7, 3);
        Fixture second = insertPendingReservationOnSameEvent(first.eventId(), 2, 5);
        send(confirmationBody(first.reservationId(), "resolution-conflict"));
        send(confirmationBody(second.reservationId(), "resolution-conflict"));

        consumer().poll();

        assertThat(reservationStatus(first.reservationId())).isEqualTo("CONFIRMED");
        assertThat(reservationStatus(second.reservationId())).isEqualTo("PENDING");
        assertThat(inboxCount("reservation-owner", "resolution-conflict")).isEqualTo(1);
        assertThat(outboxCount("ReservationConfirmed", first.reservationId())).isEqualTo(1);
        assertThat(outboxCount("ReservationConfirmed", second.reservationId())).isZero();
        var retry = sqsClient
                .receiveMessage(request -> request.queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .messageSystemAttributeNames(
                                software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
                                        .APPROXIMATE_RECEIVE_COUNT))
                .messages();
        assertThat(retry).singleElement().satisfies(message -> {
            assertThat(message.body()).contains(second.reservationId().toString());
            assertThat(message.attributes()
                            .get(
                                    software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
                                            .APPROXIMATE_RECEIVE_COUNT))
                    .isEqualTo("2");
        });
    }

    @Test
    void poll_whenCancellationCompletionMatches_releasesCapacityOnlyOnceAndKeepsCorrelation() {
        UUID cancellationId = UUID.randomUUID();
        Fixture fixture = insertCancellationPendingReservation(10, 7, 3, cancellationId);
        String body = cancellationBody(fixture.reservationId(), cancellationId, "cancel-resolution-1");
        send(body);
        send(body);

        consumer().poll();

        assertThat(reservationStatus(fixture.reservationId())).isEqualTo("CANCELLED");
        assertThat(available(fixture.eventId())).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT cancellation_id FROM reservation WHERE id = ?", UUID.class, fixture.reservationId()))
                .isEqualTo(cancellationId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT closure_reason_code FROM reservation WHERE id = ?",
                        String.class,
                        fixture.reservationId()))
                .isEqualTo("CANCELLED_BY_REQUEST");
        assertThat(inboxCount("reservation-owner", "cancel-resolution-1")).isEqualTo(1);
        assertThat(inboxOutcome("reservation-owner", "cancel-resolution-1")).isEqualTo("CANCELLED");
        awaitSourceQueueEmpty();
    }

    @Test
    void poll_whenCancellationIdDoesNotMatch_keepsCancellationPendingAndCapacityCommitted() {
        UUID actualCancellationId = UUID.randomUUID();
        Fixture fixture = insertCancellationPendingReservation(10, 7, 3, actualCancellationId);
        send(cancellationBody(fixture.reservationId(), UUID.randomUUID(), "cancel-resolution-wrong"));

        consumer().poll();

        assertThat(reservationStatus(fixture.reservationId())).isEqualTo("CANCELLATION_PENDING");
        assertThat(available(fixture.eventId())).isEqualTo(7);
        assertThat(inboxOutcome("reservation-owner", "cancel-resolution-wrong")).isEqualTo("CANCELLATION_ID_MISMATCH");
    }

    @Test
    void process_whenCancellationCannotReturnCapacity_rollsBackReservationAndInboxTogether() {
        UUID cancellationId = UUID.randomUUID();
        Fixture fixture = insertCancellationPendingReservation(10, 9, 3, cancellationId);
        ReservationResolutionMessage message = ReservationResolutionMessage.parse(
                cancellationBody(fixture.reservationId(), cancellationId, "cancel-resolution-rollback"), objectMapper);

        assertThatThrownBy(() -> processor.process(message, message.payloadFingerprint()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("could not return cancelled reservation capacity");

        assertThat(reservationStatus(fixture.reservationId())).isEqualTo("CANCELLATION_PENDING");
        assertThat(available(fixture.eventId())).isEqualTo(9);
        assertThat(inboxCount("reservation-owner", "cancel-resolution-rollback"))
                .isZero();
    }

    @Test
    void poll_whenReservationDoesNotExist_recordsNotFoundAndAcknowledgesTheMessage() {
        UUID missingReservationId = UUID.randomUUID();
        send(confirmationBody(missingReservationId, "resolution-not-found"));

        consumer().poll();

        assertThat(inboxCount("reservation-owner", "resolution-not-found")).isEqualTo(1);
        assertThat(inboxOutcome("reservation-owner", "resolution-not-found")).isEqualTo("NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT reservation_id FROM confirmation_inbox WHERE source = ? AND resolution_id = ?",
                        UUID.class,
                        "reservation-owner",
                        "resolution-not-found"))
                .isNull();
        awaitSourceQueueEmpty();
    }

    @Test
    void poll_whenConfirmationArrivesAfterDeadline_closesHoldAndPublishesRejectionOnce() {
        Fixture fixture = insertPendingReservation(10, 7, 3);
        jdbcTemplate.update(
                "UPDATE reservation SET expires_at = clock_timestamp() - interval '1 second' WHERE id = ?",
                fixture.reservationId());
        send(confirmationBody(fixture.reservationId(), "resolution-expired"));

        consumer().poll();

        assertThat(reservationStatus(fixture.reservationId())).isEqualTo("EXPIRED");
        assertThat(available(fixture.eventId())).isEqualTo(10);
        assertThat(inboxOutcome("reservation-owner", "resolution-expired")).isEqualTo("EXPIRED");
        assertThat(outboxCount("ReservationHoldClosed", fixture.reservationId()))
                .isEqualTo(1);
        assertThat(outboxCount("ReservationConfirmationRejected", fixture.reservationId()))
                .isEqualTo(1);
        assertThat(outboxCount("ReservationConfirmed", fixture.reservationId())).isZero();
    }

    @Test
    void localOwnerSimulation_holdsConfirmsAndCompletesCancellationWithoutPaymentProcessing() throws Exception {
        UUID eventId = insertEvent(10, 10);
        var created = createReservationService.create(eventId, 4, "Simulation", "simulation@example.com");
        UUID reservationId = created.reservation().id();
        OutboxSqsPublisher publisher = new OutboxSqsPublisher(
                outboxEventStore,
                sqsClient,
                expirationQueueUrl,
                notificationQueueUrl,
                ownerQueueUrl,
                java.time.Clock.systemUTC(),
                objectMapper);

        publisher.publishPendingEvents();
        var heldMessage = receiveOwnerEvent("ReservationHeld");
        var held = objectMapper.readTree(heldMessage.body());
        assertThat(held.get("reservationId").asString()).isEqualTo(reservationId.toString());
        assertThat(held.get("quantity").asInt()).isEqualTo(4);
        assertThat(held.has("customerId")).isFalse();
        sqsClient.deleteMessage(request -> request.queueUrl(ownerQueueUrl).receiptHandle(heldMessage.receiptHandle()));

        send(confirmationBody(reservationId, "simulation-confirmation"));
        consumer().poll();
        assertThat(reservationStatus(reservationId)).isEqualTo("CONFIRMED");
        assertThat(available(eventId)).isEqualTo(6);

        cancelReservationService.cancel(reservationId);
        publisher.publishPendingEvents();
        var cancellationMessage = receiveOwnerEvent("ReservationCancellationRequested");
        UUID cancellationId = UUID.fromString(objectMapper
                .readTree(cancellationMessage.body())
                .get("cancellationId")
                .asString());
        sqsClient.deleteMessage(
                request -> request.queueUrl(ownerQueueUrl).receiptHandle(cancellationMessage.receiptHandle()));

        send(cancellationBody(reservationId, cancellationId, "simulation-cancellation-completed"));
        consumer().poll();
        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELLED");
        assertThat(available(eventId)).isEqualTo(10);
        assertThat(outboxCount("ReservationCancellationRequested", reservationId))
                .isEqualTo(1);
    }

    @Test
    void poll_whenEnvelopeIsMalformed_leavesTheMessageForRetry() {
        String body = "{\"version\":1}";
        send(body);

        consumer().poll();

        var retry = sqsClient
                .receiveMessage(request -> request.queueUrl(queueUrl)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(1)
                        .messageSystemAttributeNames(
                                software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
                                        .APPROXIMATE_RECEIVE_COUNT))
                .messages();
        assertThat(retry).singleElement().satisfies(message -> {
            assertThat(message.body()).isEqualTo(body);
            assertThat(message.attributes()
                            .get(
                                    software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
                                            .APPROXIMATE_RECEIVE_COUNT))
                    .isEqualTo("2");
        });
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM confirmation_inbox", Integer.class))
                .isZero();
    }

    private SqsReservationConfirmationConsumer consumer() {
        return new SqsReservationConfirmationConsumer(sqsClient, processor, queueUrl, objectMapper);
    }

    private String createQueue(String name, Map<QueueAttributeName, String> attributes) {
        String url = sqsClient
                .createQueue(request -> request.queueName(name).attributes(attributes))
                .queueUrl();
        lastCreatedQueueUrl = url;
        return url;
    }

    private software.amazon.awssdk.services.sqs.model.Message receiveOwnerEvent(String type) {
        var messages = sqsClient
                .receiveMessage(request ->
                        request.queueUrl(ownerQueueUrl).maxNumberOfMessages(10).waitTimeSeconds(1))
                .messages();
        return messages.stream()
                .filter(message -> {
                    try {
                        return type.equals(objectMapper
                                .readTree(message.body())
                                .get("type")
                                .asString());
                    } catch (Exception exception) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing owner event: " + type));
    }

    private Map<QueueAttributeName, String> sourceQueueAttributes(int maxReceiveCount, int visibilityTimeout) {
        String redrivePolicy = "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"%d\"}"
                .formatted(queueArn(deadLetterQueueUrl), maxReceiveCount);
        return Map.of(
                QueueAttributeName.REDRIVE_POLICY,
                redrivePolicy,
                QueueAttributeName.VISIBILITY_TIMEOUT,
                Integer.toString(visibilityTimeout));
    }

    private void setSourceQueueAttributes(Map<QueueAttributeName, String> attributes) {
        sqsClient.setQueueAttributes(request -> request.queueUrl(queueUrl).attributes(attributes));
    }

    private String queueArn(String url) {
        return sqsClient
                .getQueueAttributes(request -> request.queueUrl(url).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributesAsStrings()
                .get(QueueAttributeName.QUEUE_ARN);
    }

    private int queueCount(String url, QueueAttributeName attribute) {
        return Integer.parseInt(sqsClient
                .getQueueAttributes(request -> request.queueUrl(url).attributeNames(attribute))
                .attributesAsStrings()
                .getOrDefault(attribute, "0"));
    }

    private String send(String body) {
        return sqsClient
                .sendMessage(request -> request.queueUrl(queueUrl).messageBody(body))
                .messageId();
    }

    private String confirmationBody(UUID reservationId, String resolutionId) {
        return """
                {"version":1,"type":"ReservationConfirmationRequested","source":"reservation-owner",
                 "resolutionId":"%s","reservationId":"%s","requestedAt":"2026-09-29T14:00:00Z"}
                """.formatted(resolutionId, reservationId);
    }

    private String cancellationBody(UUID reservationId, UUID cancellationId, String resolutionId) {
        return """
                {"version":1,"type":"ReservationCancellationCompleted","source":"reservation-owner",
                 "resolutionId":"%s","reservationId":"%s","cancellationId":"%s"}
                """.formatted(resolutionId, reservationId, cancellationId);
    }

    private Fixture insertPendingReservation(int capacity, int available, int quantity) {
        UUID eventId = insertEvent(capacity, available);
        return insertPendingReservationOnSameEvent(eventId, quantity, available);
    }

    private Fixture insertPendingReservationOnSameEvent(UUID eventId, int quantity, int available) {
        jdbcTemplate.update("UPDATE event SET available = ? WHERE id = ?", available, eventId);
        UUID customerId = insertCustomer();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(60);
        Instant expiresAt = Instant.now().plusSeconds(600);
        insertReservation(reservationId, eventId, customerId, quantity, "PENDING", expiresAt, null, null, createdAt);
        return new Fixture(eventId, reservationId);
    }

    private Fixture insertCancellationPendingReservation(
            int capacity, int available, int quantity, UUID cancellationId) {
        UUID eventId = insertEvent(capacity, available);
        UUID customerId = insertCustomer();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(600);
        Instant expiresAt = Instant.now().plusSeconds(600);
        insertReservation(
                reservationId,
                eventId,
                customerId,
                quantity,
                "CANCELLATION_PENDING",
                expiresAt,
                Instant.now().minusSeconds(30),
                cancellationId,
                createdAt);
        return new Fixture(eventId, reservationId);
    }

    private void insertReservation(
            UUID reservationId,
            UUID eventId,
            UUID customerId,
            int quantity,
            String status,
            Instant expiresAt,
            Instant confirmedAt,
            UUID cancellationId,
            Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO reservation (
                    id, event_id, customer_id, quantity, status, expires_at, confirmed_at, cancellation_id,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                reservationId,
                eventId,
                customerId,
                quantity,
                status,
                Timestamp.from(expiresAt),
                confirmedAt == null ? null : Timestamp.from(confirmedAt),
                cancellationId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private UUID insertEvent(int capacity, int available) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                eventId,
                "Confirmation event",
                capacity,
                available,
                Timestamp.from(Instant.now().minusSeconds(1200)));
        return eventId;
    }

    private UUID insertCustomer() {
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(60);
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId,
                "Resolution customer",
                customerId + "@example.com",
                Timestamp.from(now),
                Timestamp.from(now));
        return customerId;
    }

    private String reservationStatus(UUID reservationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, reservationId);
    }

    private int available(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT available FROM event WHERE id = ?", Integer.class, eventId);
    }

    private int inboxCount(String source, String resolutionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM confirmation_inbox WHERE source = ? AND resolution_id = ?",
                Integer.class,
                source,
                resolutionId);
    }

    private String inboxOutcome(String source, String resolutionId) {
        return jdbcTemplate.queryForObject(
                "SELECT outcome->>'code' FROM confirmation_inbox WHERE source = ? AND resolution_id = ?",
                String.class,
                source,
                resolutionId);
    }

    private int outboxCount(String eventType, UUID reservationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                Integer.class,
                eventType,
                reservationId);
    }

    private void awaitSourceQueueEmpty() {
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(queueCount(queueUrl, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                            .isZero();
                    assertThat(queueCount(queueUrl, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
                            .isZero();
                });
    }

    private record Fixture(UUID eventId, UUID reservationId) {}
}
