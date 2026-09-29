package com.cielo.flashbooking.reservation.confirm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ConfirmationConsumerProperties.class)
@Profile({"worker", "all"})
@ConditionalOnBean(SqsClient.class)
@ConditionalOnProperty(prefix = "confirmation.consumer", name = "enabled", havingValue = "true")
class SqsReservationConfirmationConsumerConfiguration {

    @Bean
    SqsReservationConfirmationConsumer sqsReservationConfirmationConsumer(
            SqsClient sqsClient,
            ReservationResolutionProcessor processor,
            ConfirmationConsumerProperties properties,
            JsonMapper objectMapper) {
        Assert.hasText(properties.queueUrl(), "confirmation.consumer.queue-url must be configured");
        return new SqsReservationConfirmationConsumer(sqsClient, processor, properties.queueUrl(), objectMapper);
    }
}
