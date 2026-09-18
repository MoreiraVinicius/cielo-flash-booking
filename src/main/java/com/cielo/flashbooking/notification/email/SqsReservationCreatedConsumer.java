package com.cielo.flashbooking.notification.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public class SqsReservationCreatedConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsReservationCreatedConsumer.class);

    private final SqsClient sqsClient;
    private final ReservationEmailService reservationEmailService;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public SqsReservationCreatedConsumer(
            SqsClient sqsClient,
            ReservationEmailService reservationEmailService,
            String queueUrl,
            ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.reservationEmailService = reservationEmailService;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${notification.consumer.fixed-delay:1s}")
    public void poll() {
        sqsClient.receiveMessage(request -> request
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .messageAttributeNames("All")
                        .waitTimeSeconds(1))
                .messages()
                .forEach(this::process);
    }

    private void process(Message message) {
        try {
            requireReservationCreated(message);
            UUID outboxEventId = UUID.fromString(requiredAttribute(message, "outboxEventId"));
            UUID reservationId = UUID.fromString(objectMapper.readTree(message.body())
                    .required("reservationId")
                    .asText());
            ReservationEmailService.ProcessingResult result = reservationEmailService.process(outboxEventId, reservationId);
            if (result.acknowledged()) {
                sqsClient.deleteMessage(request -> request.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            } else {
                LOGGER.warn("notification delivery remains available for retry: outboxEventId={}", outboxEventId);
            }
        } catch (Exception exception) {
            LOGGER.warn("notification message remains available for retry exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void requireReservationCreated(Message message) {
        if (!"ReservationCreated".equals(requiredAttribute(message, "eventType"))) {
            throw new IllegalArgumentException("notification queue received an unsupported event type");
        }
    }

    private String requiredAttribute(Message message, String attributeName) {
        MessageAttributeValue attribute = message.messageAttributes().get(attributeName);
        if (attribute == null || attribute.stringValue() == null) {
            throw new IllegalArgumentException("notification message is missing " + attributeName);
        }
        return attribute.stringValue();
    }
}
