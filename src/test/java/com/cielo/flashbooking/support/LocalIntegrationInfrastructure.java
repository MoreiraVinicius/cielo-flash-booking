package com.cielo.flashbooking.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.localstack.LocalStackContainer;

@Testcontainers
abstract class LocalIntegrationInfrastructure {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    protected static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.0-alpine"))
            .withExposedPorts(6379);

    @Container
    protected static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(LocalStackContainer.Service.SQS);

    @Container
    protected static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.21.8"))
            .withExposedPorts(8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forStatusCode(200));
}
