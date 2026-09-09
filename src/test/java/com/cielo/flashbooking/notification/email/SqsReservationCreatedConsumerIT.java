package com.cielo.flashbooking.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.adapter.out.persistence.notification.JdbcNotificationDeliveryStore;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import com.cielo.flashbooking.support.LocalIntegrationInfrastructure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

@SpringBootTest
class SqsReservationCreatedConsumerIT extends LocalIntegrationInfrastructure {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(6379));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservationReader reservationReader;

    private SqsClient sqsClient;
    private String queueUrl;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM notification_delivery");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM event");
        clearMailbox();
        sqsClient = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();
        queueUrl = sqsClient.createQueue(request -> request.queueName("notification-" + UUID.randomUUID())).queueUrl();
    }

    @AfterEach
    void closeClient() {
        sqsClient.close();
    }

    @Test
    void deliversTemporaryReservationDetailsThroughMailpitWithinTheWorkerFlow() throws Exception {
        UUID reservationId = insertPendingReservation();
        UUID outboxEventId = insertReservationCreatedEvent(reservationId);
        send(outboxEventId, reservationId);

        consumer().poll();

        String email = deliveredEmail();
        assertThat(email).contains(
                reservationId.toString(),
                "Email event",
                "Quantidade: 2",
                "Esta reserva é temporária e não confirma compra nem pagamento.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM notification_delivery WHERE outbox_event_id = ?", String.class, outboxEventId))
                .isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempts FROM notification_delivery WHERE outbox_event_id = ?", Integer.class, outboxEventId))
                .isEqualTo(1);
        assertThat(sqsClient.receiveMessage(request -> request.queueUrl(queueUrl)).messages()).isEmpty();
    }

    @Test
    void acknowledgesKnownDuplicateWithoutSendingAnotherEmail() throws Exception {
        UUID reservationId = insertPendingReservation();
        UUID outboxEventId = insertReservationCreatedEvent(reservationId);
        send(outboxEventId, reservationId);
        consumer().poll();
        send(outboxEventId, reservationId);

        consumer().poll();

        assertThat(mailboxMessages().path("messages")).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempts FROM notification_delivery WHERE outbox_event_id = ?", Integer.class, outboxEventId))
                .isEqualTo(1);
    }

    private SqsReservationCreatedConsumer consumer() {
        ReservationEmailService service = new ReservationEmailService(
                new JdbcNotificationDeliveryStore(jdbcTemplate),
                reservationReader,
                new SmtpReservationEmailSender(mailSender(), "reservas@example.com"),
                new NotificationConsumerProperties(false, queueUrl, 3, 2));
        return new SqsReservationCreatedConsumer(sqsClient, service, queueUrl, objectMapper);
    }

    private JavaMailSenderImpl mailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(MAILPIT.getHost());
        sender.setPort(MAILPIT.getMappedPort(1025));
        sender.setProtocol("smtp");
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "3000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "3000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "3000");
        return sender;
    }

    private void send(UUID outboxEventId, UUID reservationId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("reservationId", reservationId.toString()));
        sqsClient.sendMessage(request -> request
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageAttributes(Map.of(
                        "eventType", messageAttribute("ReservationCreated"),
                        "outboxEventId", messageAttribute(outboxEventId.toString()))));
    }

    private MessageAttributeValue messageAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private UUID insertPendingReservation() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Instant createdAt = databaseNow();
        Instant expiresAt = createdAt.plusSeconds(600);
        jdbcTemplate.update(
                "INSERT INTO event (id, name, capacity, available, created_at) VALUES (?, ?, ?, ?, ?)",
                eventId, "Email event", 10, 8, java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update(
                "INSERT INTO customer (id, name, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId, "Email customer", "customer@example.com", java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        jdbcTemplate.update("""
                INSERT INTO reservation (id, event_id, customer_id, quantity, status, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """, reservationId, eventId, customerId, 2, java.sql.Timestamp.from(expiresAt),
                java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        return reservationId;
    }

    private UUID insertReservationCreatedEvent(UUID reservationId) throws Exception {
        UUID outboxEventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
                VALUES (?, 'Reservation', ?, 'ReservationCreated', ?::jsonb, ?)
                """, outboxEventId, reservationId,
                objectMapper.writeValueAsString(Map.of("reservationId", reservationId.toString())),
                java.sql.Timestamp.from(databaseNow()));
        return outboxEventId;
    }

    private Instant databaseNow() {
        return jdbcTemplate.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class).toInstant();
    }

    private void clearMailbox() throws Exception {
        HttpResponse<Void> response = HTTP_CLIENT.send(HttpRequest.newBuilder(mailpitUri("/api/v1/messages"))
                .DELETE()
                .timeout(Duration.ofSeconds(3))
                .build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    private String deliveredEmail() throws Exception {
        JsonNode messages = mailboxMessages().required("messages");
        assertThat(messages).hasSize(1);
        String messageId = messages.get(0).required("ID").asText();
        HttpResponse<String> response = HTTP_CLIENT.send(HttpRequest.newBuilder(mailpitUri("/api/v1/message/" + messageId))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private JsonNode mailboxMessages() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(HttpRequest.newBuilder(mailpitUri("/api/v1/messages"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private URI mailpitUri(String path) {
        return URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path);
    }
}
