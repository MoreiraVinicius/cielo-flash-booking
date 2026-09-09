package com.cielo.flashbooking.feature.reservation.expire;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile({"worker", "all"})
@ConditionalOnBean(SqsClient.class)
@ConditionalOnProperty(prefix = "expiration.consumer", name = "enabled", havingValue = "true")
class SqsExpirationConsumerConfiguration {

    @Bean
    SqsExpirationConsumer sqsExpirationConsumer(
            SqsClient sqsClient,
            ExpireReservationService expireReservationService,
            ExpirationConsumerProperties properties) {
        Assert.hasText(properties.queueUrl(), "expiration.consumer.queue-url must be configured");
        return new SqsExpirationConsumer(sqsClient, expireReservationService, properties.queueUrl());
    }
}
