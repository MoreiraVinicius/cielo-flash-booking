package com.cielo.flashbooking.config;

import com.cielo.flashbooking.event.summary.ExecutiveSummaryProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

@Configuration(proxyBeanMethods = false)
@Profile({"worker", "all"})
class ExecutiveSummaryAwsConfiguration {

    @Bean(destroyMethod = "close")
    BedrockRuntimeClient bedrockRuntimeClient(ExecutiveSummaryProperties properties) {
        Duration timeout = properties.getBedrock().getApiTimeout();
        return BedrockRuntimeClient.builder()
                .region(Region.of(properties.getBedrock().getRegion()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(timeout)
                        .apiCallTimeout(timeout)
                        .retryPolicy(RetryPolicy.none())
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    CloudWatchClient cloudWatchClient(ExecutiveSummaryProperties properties) {
        Duration timeout = properties.getBedrock().getApiTimeout();
        return CloudWatchClient.builder()
                .region(Region.of(properties.getBedrock().getRegion()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(timeout)
                        .apiCallTimeout(timeout)
                        .retryPolicy(RetryPolicy.none())
                        .build())
                .build();
    }
}
