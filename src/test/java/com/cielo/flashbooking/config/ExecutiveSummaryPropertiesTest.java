package com.cielo.flashbooking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.event.summary.ExecutiveSummaryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ExecutiveSummaryPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsModelAndAllowlistedOperationalSignals() {
        contextRunner
                .withPropertyValues(
                        "executive-summary.bedrock.model-id=amazon.nova-micro-v1:0",
                        "executive-summary.bedrock.api-timeout=12s",
                        "executive-summary.operational-signals.alarms[0].name=demo-api-5xx",
                        "executive-summary.operational-signals.alarms[0].label=Gateway com falhas temporarias")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(ExecutiveSummaryProperties.class);
                    assertThat(properties.getBedrock().getApiTimeout()).hasSeconds(12);
                    assertThat(properties.getOperationalSignals().getAlarms()).hasSize(1);
                    assertThat(properties
                                    .getOperationalSignals()
                                    .getAlarms()
                                    .getFirst()
                                    .getLabel())
                            .isEqualTo("Gateway com falhas temporarias");
                });
    }

    @Test
    void rejectsNonPositiveOrUnboundedCallTimeout() {
        contextRunner
                .withPropertyValues("executive-summary.bedrock.api-timeout=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                });

        contextRunner
                .withPropertyValues("executive-summary.bedrock.api-timeout=61s")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ExecutiveSummaryProperties.class)
    static class PropertiesConfiguration {}
}
