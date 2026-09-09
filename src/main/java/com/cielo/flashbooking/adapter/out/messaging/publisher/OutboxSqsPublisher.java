package com.cielo.flashbooking.adapter.out.messaging.publisher;

import com.cielo.flashbooking.application.outbox.OutboxEvent;
import com.cielo.flashbooking.application.outbox.OutboxEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class OutboxSqsPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxSqsPublisher.class);
    private static final int BATCH_SIZE = 100;
    private static final long MAX_DELAY_SECONDS = 900;

    private final OutboxEventStore outboxEventStore;
    private final SqsClient sqsClient;
    private final String expirationQueueUrl;
    private final String notificationQueueUrl;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OutboxSqsPublisher(
            OutboxEventStore outboxEventStore,
            SqsClient sqsClient,
            String expirationQueueUrl,
            String notificationQueueUrl,
            Clock clock) {
        this.outboxEventStore = outboxEventStore;
        this.sqsClient = sqsClient;
        this.expirationQueueUrl = expirationQueueUrl;
        this.notificationQueueUrl = notificationQueueUrl;
        this.clock = clock;
        this.objectMapper = new ObjectMapper();
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:1s}")
    public void publishPendingEvents() {
        outboxEventStore.findPending(BATCH_SIZE).forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        try {
            outboxEventStore.recordAttempt(event.id());
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrlFor(event))
                    .messageBody(event.payload())
                    .messageAttributes(Map.of("eventType", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(event.eventType())
                            .build()))
                    .delaySeconds(delaySecondsFor(event))
                    .build());
            outboxEventStore.markPublished(event.id(), clock.instant());
        } catch (Exception exception) {
            LOGGER.warn("outbox event {} remains pending after SQS publication failure", event.id(), exception);
        }
    }

    private String queueUrlFor(OutboxEvent event) {
        return switch (event.eventType()) {
            case "ReservationCreated" -> notificationQueueUrl;
            case "ReservationExpirationScheduled" -> expirationQueueUrl;
            default -> throw new IllegalArgumentException("unsupported outbox event type: " + event.eventType());
        };
    }

    private int delaySecondsFor(OutboxEvent event) {
        if (!"ReservationExpirationScheduled".equals(event.eventType())) {
            return 0;
        }
        try {
            Instant expiresAt = Instant.parse(objectMapper.readTree(event.payload()).required("expiresAt").asText());
            Duration remaining = Duration.between(clock.instant(), expiresAt);
            if (remaining.isNegative() || remaining.isZero()) {
                return 0;
            }
            long milliseconds = remaining.toMillis();
            long seconds = (milliseconds + 999) / 1000;
            return (int) Math.min(MAX_DELAY_SECONDS, seconds);
        } catch (Exception exception) {
            throw new IllegalArgumentException("outbox expiration event has an invalid expiresAt", exception);
        }
    }
}
