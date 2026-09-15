package com.cielo.flashbooking.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class IdempotencyCleanupPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void binding_whenSettingsAreAbsent_usesSafeDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            IdempotencyCleanupProperties properties = context.getBean(IdempotencyCleanupProperties.class);
            assertThat(properties.batchSize()).isEqualTo(500);
            assertThat(properties.fixedDelay()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.initialDelay()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void binding_whenEnvironmentOverridesAreProvided_bindsExplicitUnitsAndValues() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("cleanup-test-systemEnvironment", Map.<String, Object>of(
                        "IDEMPOTENCY_CLEANUP_BATCHSIZE", "25",
                        "IDEMPOTENCY_CLEANUP_FIXEDDELAY", "2s",
                        "IDEMPOTENCY_CLEANUP_INITIALDELAY", "0s"))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    IdempotencyCleanupProperties properties = context.getBean(IdempotencyCleanupProperties.class);
                    assertThat(properties.batchSize()).isEqualTo(25);
                    assertThat(properties.fixedDelay()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.initialDelay()).isEqualTo(Duration.ZERO);
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10_001})
    void binding_whenBatchSizeIsOutsideSafeBounds_failsStartup(int batchSize) {
        contextRunner.withPropertyValues("idempotency.cleanup.batch-size=" + batchSize)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    void binding_whenFixedDelayIsNotPositive_failsStartup(String fixedDelay) {
        contextRunner.withPropertyValues("idempotency.cleanup.fixed-delay=" + fixedDelay)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_whenInitialDelayIsNegative_failsStartup() {
        contextRunner.withPropertyValues("idempotency.cleanup.initial-delay=-1s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IdempotencyCleanupProperties.class)
    static class PropertiesConfiguration {
    }
}
