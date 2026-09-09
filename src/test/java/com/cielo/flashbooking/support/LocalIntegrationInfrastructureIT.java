package com.cielo.flashbooking.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

class LocalIntegrationInfrastructureIT extends LocalIntegrationInfrastructure {

    @Test
    void startsAndExposesEveryRequiredLocalIntegrationService() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }

        try (var socket = new Socket(VALKEY.getHost(), VALKEY.getMappedPort(6379));
                var writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("PING\r\n");
            writer.flush();
            assertThat(reader.readLine()).isEqualTo("+PONG");
        }

        try (var sqs = sqsClient()) {
            var queueUrl = sqs.createQueue(request -> request.queueName("integration-" + UUID.randomUUID())).queueUrl();
            assertThat(sqs.listQueues().queueUrls()).contains(queueUrl);
        }

        var endpoint = "http://%s:%d/api/v1/messages".formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(8025));
        var connection = (HttpURLConnection) new java.net.URI(endpoint).toURL().openConnection();
        connection.setRequestMethod("GET");
        assertThat(connection.getResponseCode()).isEqualTo(200);
    }

    private SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();
    }
}
