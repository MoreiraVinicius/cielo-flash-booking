package com.cielo.flashbooking.feature.reservation.expire;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

public class SqsExpirationConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsExpirationConsumer.class);

    private final SqsClient sqsClient;
    private final ExpireReservationService expireReservationService;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public SqsExpirationConsumer(
            SqsClient sqsClient,
            ExpireReservationService expireReservationService,
            String queueUrl) {
        this.sqsClient = sqsClient;
        this.expireReservationService = expireReservationService;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Scheduled(fixedDelayString = "${expiration.consumer.fixed-delay:1s}")
    public void poll() {
        sqsClient.receiveMessage(request -> request
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1))
                .messages()
                .forEach(this::process);
    }

    private void process(Message message) {
        try {
            UUID reservationId = UUID.fromString(objectMapper.readTree(message.body())
                    .required("reservationId")
                    .asText());
            expireReservationService.expire(reservationId);
            sqsClient.deleteMessage(request -> request.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        } catch (Exception exception) {
            LOGGER.warn("expiration message remains available for retry", exception);
        }
    }
}
