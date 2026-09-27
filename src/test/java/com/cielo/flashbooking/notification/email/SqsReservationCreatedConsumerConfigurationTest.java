package com.cielo.flashbooking.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SqsReservationCreatedConsumerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SqsReservationCreatedConsumerConfiguration.class)
            .withPropertyValues("spring.profiles.active=worker", "notification.consumer.enabled=false");

    @Test
    void notificationConsumerDisabled_doesNotCreateSqsConsumer() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(SqsReservationCreatedConsumer.class));
    }
}
