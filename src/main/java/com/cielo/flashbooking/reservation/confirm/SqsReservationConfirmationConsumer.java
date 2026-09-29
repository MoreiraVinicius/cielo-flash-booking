package com.cielo.flashbooking.reservation.confirm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.json.JsonMapper;

public class SqsReservationConfirmationConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsReservationConfirmationConsumer.class);
    private static final int MAX_MESSAGES = 10;
    private static final int LONG_POLL_SECONDS = 20;

    private final SqsClient sqsClient;
    private final ReservationResolutionProcessor processor;
    private final String queueUrl;
    private final JsonMapper objectMapper;

    public SqsReservationConfirmationConsumer(
            SqsClient sqsClient, ReservationResolutionProcessor processor, String queueUrl, JsonMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.processor = processor;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${confirmation.consumer.fixed-delay:1s}")
    public void poll() {
        sqsClient
                .receiveMessage(request -> request.queueUrl(queueUrl)
                        .maxNumberOfMessages(MAX_MESSAGES)
                        .waitTimeSeconds(LONG_POLL_SECONDS))
                .messages()
                .forEach(this::process);
    }

    private void process(Message message) {
        try {
            ReservationResolutionMessage resolution = ReservationResolutionMessage.parse(message.body(), objectMapper);
            processor.process(resolution, resolution.payloadFingerprint());
            sqsClient.deleteMessage(request -> request.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "reservation resolution message remains in queue for retry messageId={} exceptionType={}",
                    message.messageId(),
                    exception.getClass().getSimpleName());
        }
    }
}
