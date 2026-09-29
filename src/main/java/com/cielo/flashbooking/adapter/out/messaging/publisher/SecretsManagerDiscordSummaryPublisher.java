package com.cielo.flashbooking.adapter.out.messaging.publisher;

import com.cielo.flashbooking.event.summary.DiscordSummaryMessageFormatter;
import com.cielo.flashbooking.event.summary.DiscordSummaryPublisher;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile({"worker", "all"})
class SecretsManagerDiscordSummaryPublisher implements DiscordSummaryPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretsManagerDiscordSummaryPublisher.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private final SecretsManagerClient secretsManagerClient;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final DiscordSummaryMessageFormatter formatter;
    private volatile CachedWebhook cachedWebhook;

    SecretsManagerDiscordSummaryPublisher(
            SecretsManagerClient secretsManagerClient,
            HttpClient httpClient,
            JsonMapper jsonMapper,
            DiscordSummaryMessageFormatter formatter) {
        this.secretsManagerClient = secretsManagerClient;
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.formatter = formatter;
    }

    @Override
    public DeliveryStatus publish(String webhookSecretArn, String markdown) {
        URI webhook;
        try {
            webhook = webhook(webhookSecretArn);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Executive summary Discord webhook unavailable: {}",
                    exception.getClass().getSimpleName());
            return DeliveryStatus.FAILED;
        }

        try {
            String content = formatter.format(markdown);
            if (content.length() > 2_000) {
                return DeliveryStatus.FAILED;
            }
            String payload = jsonMapper.writeValueAsString(
                    Map.of("content", content, "allowed_mentions", Map.of("parse", List.of())));
            HttpRequest request = HttpRequest.newBuilder(webhook)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300
                    ? DeliveryStatus.SENT
                    : DeliveryStatus.FAILED;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return DeliveryStatus.UNKNOWN;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Executive summary Discord response is ambiguous: {}",
                    exception.getClass().getSimpleName());
            return DeliveryStatus.UNKNOWN;
        }
    }

    private URI webhook(String secretArn) {
        CachedWebhook current = cachedWebhook;
        if (current != null && current.secretArn().equals(secretArn)) {
            return current.uri();
        }
        synchronized (this) {
            current = cachedWebhook;
            if (current != null && current.secretArn().equals(secretArn)) {
                return current.uri();
            }
            String secret = secretsManagerClient
                    .getSecretValue(
                            GetSecretValueRequest.builder().secretId(secretArn).build())
                    .secretString();
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("Discord webhook secret is empty");
            }
            URI uri = URI.create(secret.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || !(host.equalsIgnoreCase("discord.com") || host.equalsIgnoreCase("discordapp.com"))
                    || uri.getPath() == null
                    || !uri.getPath().matches("/api/webhooks/[^/]+/[^/]+/?")) {
                throw new IllegalArgumentException("Discord webhook URL is invalid");
            }
            cachedWebhook = new CachedWebhook(secretArn, uri);
            return uri;
        }
    }

    private record CachedWebhook(String secretArn, URI uri) {}
}
