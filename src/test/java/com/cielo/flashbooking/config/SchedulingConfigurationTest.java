package com.cielo.flashbooking.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfiguration.class)
            .withPropertyValues("spring.task.scheduling.pool.size=4");

    @Test
    void scheduling_whenJobsAreEnabled_runsIndependentTasksConcurrently() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ThreadPoolTaskScheduler scheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
            CountDownLatch bothStarted = new CountDownLatch(2);
            CountDownLatch releaseTasks = new CountDownLatch(1);
            Runnable blockingTask = () -> {
                bothStarted.countDown();
                try {
                    releaseTasks.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            };
            scheduler.schedule(blockingTask, Instant.now());
            scheduler.schedule(blockingTask, Instant.now());
            try {
                assertThat(bothStarted.await(1, TimeUnit.SECONDS)).isTrue();
            } finally {
                releaseTasks.countDown();
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfiguration {
    }
}
