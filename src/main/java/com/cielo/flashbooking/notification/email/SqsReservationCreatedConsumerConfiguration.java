package com.cielo.flashbooking.notification.email;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.SesV2ClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import com.cielo.flashbooking.reservation.application.ReservationReader;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Profile({"worker", "all"})
@ConditionalOnProperty(prefix = "notification.consumer", name = "enabled", havingValue = "true")
class SqsReservationCreatedConsumerConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "notification.email", name = "provider", havingValue = "smtp")
    ReservationEmailSender smtpReservationEmailSender(JavaMailSender mailSender, NotificationEmailProperties properties) {
        Assert.hasText(properties.fromAddress(), "notification.email.from-address must be configured");
        return new SmtpReservationEmailSender(mailSender, properties.fromAddress());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "notification.email", name = "provider", havingValue = "ses")
    SesV2Client sesV2Client(NotificationEmailProperties properties) {
        Assert.hasText(properties.fromAddress(), "notification.email.from-address must be configured");
        SesV2ClientBuilder builder = SesV2Client.builder()
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
    @ConditionalOnBean(SesV2Client.class)
    ReservationEmailSender sesReservationEmailSender(SesV2Client sesV2Client, NotificationEmailProperties properties) {
        return new SesReservationEmailSender(sesV2Client, properties.fromAddress());
    }

    @Bean
    ReservationEmailService reservationEmailService(
            NotificationDeliveryStore notificationDeliveryStore,
            ReservationReader reservationReader,
            ReservationEmailSender reservationEmailSender,
            NotificationConsumerProperties properties) {
        return new ReservationEmailService(
                notificationDeliveryStore,
                reservationReader,
                reservationEmailSender,
                properties);
    }

    @Bean
    SqsReservationCreatedConsumer sqsReservationCreatedConsumer(
            SqsClient sqsClient,
            ReservationEmailService reservationEmailService,
            NotificationConsumerProperties properties,
            ObjectMapper objectMapper) {
        Assert.hasText(properties.queueUrl(), "notification.consumer.queue-url must be configured");
        return new SqsReservationCreatedConsumer(sqsClient, reservationEmailService, properties.queueUrl(), objectMapper);
    }
}
