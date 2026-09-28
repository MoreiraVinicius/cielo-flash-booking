package com.cielo.flashbooking.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

class ObservabilityConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void disablesRemoteTelemetryByDefault() {
        contextRunner.run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("management.tracing.export.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("management.tracing.export.otlp.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("management.opentelemetry.logging.export.otlp.enabled", Boolean.class))
                    .isFalse();
        });
    }

    @Test
    void enablesMetricsAndTraceExportOnlyForTheOptInProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=observability").run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("management.tracing.export.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("management.tracing.export.otlp.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("management.otlp.metrics.export.url"))
                    .isEqualTo("http://localhost:4318/v1/metrics");
            assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                    .isEqualTo("http://localhost:4318/v1/traces");
            assertThat(environment.getProperty("management.tracing.sampling.probability", Double.class))
                    .isEqualTo(1.0);
        });
    }
}
