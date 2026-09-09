package com.cielo.flashbooking.adapter.out.messaging.publisher;

import com.cielo.flashbooking.application.outbox.OutboxEventStore;
import java.net.URI;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true")
class SqsPublisherConfiguration {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(OutboxPublisherProperties properties) {
        Assert.hasText(properties.expirationQueueUrl(), "outbox.publisher.expiration-queue-url must be configured");
        Assert.hasText(properties.notificationQueueUrl(), "outbox.publisher.notification-queue-url must be configured");
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(properties.apiCallTimeout())
                        .apiCallTimeout(properties.apiCallTimeout())
                        .build());
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean
    OutboxSqsPublisher outboxSqsPublisher(
            OutboxEventStore outboxEventStore,
            SqsClient sqsClient,
            OutboxPublisherProperties properties,
            Clock clock) {
        return new OutboxSqsPublisher(
                outboxEventStore,
                sqsClient,
                properties.expirationQueueUrl(),
                properties.notificationQueueUrl(),
                clock);
    }
}
