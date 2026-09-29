package com.cielo.flashbooking.adapter.out.messaging.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.event.summary.DiscordSummaryMessageFormatter;
import com.cielo.flashbooking.event.summary.DiscordSummaryPublisher;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import tools.jackson.databind.json.JsonMapper;

class SecretsManagerDiscordSummaryPublisherTest {

    private final SecretsManagerClient secrets = mock(SecretsManagerClient.class);
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final SecretsManagerDiscordSummaryPublisher publisher = new SecretsManagerDiscordSummaryPublisher(
            secrets, http, JsonMapper.builder().build(), new DiscordSummaryMessageFormatter());

    @Test
    void readsSecretLazilyCachesItAndPostsOnlyToValidatedDiscordWebhook() throws Exception {
        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString("https://discord.com/api/webhooks/123/token")
                        .build());
        when(response.statusCode()).thenReturn(204);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        assertThat(publisher.publish("secret-arn", "# Event")).isEqualTo(DiscordSummaryPublisher.DeliveryStatus.SENT);
        assertThat(publisher.publish("secret-arn", "# Event")).isEqualTo(DiscordSummaryPublisher.DeliveryStatus.SENT);

        verify(secrets).getSecretValue(any(GetSecretValueRequest.class));
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, org.mockito.Mockito.times(2)).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getAllValues())
                .allSatisfy(value ->
                        assertThat(value.uri().toString()).isEqualTo("https://discord.com/api/webhooks/123/token"));
    }

    @Test
    void malformedSecretDoesNotSendAndHttpTimeoutLeavesDeliveryUnknown() throws Exception {
        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString("https://example.com/post")
                        .build());
        assertThat(publisher.publish("secret-arn", "# Event")).isEqualTo(DiscordSummaryPublisher.DeliveryStatus.FAILED);
        verify(http, org.mockito.Mockito.never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        when(secrets.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString("https://discord.com/api/webhooks/123/token")
                        .build());
        when(response.statusCode()).thenReturn(500);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        assertThat(publisher.publish("definitive-error-secret", "# Event"))
                .isEqualTo(DiscordSummaryPublisher.DeliveryStatus.FAILED);

        org.mockito.Mockito.reset(http);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("timeout"));
        assertThat(publisher.publish("other-secret-arn", "# Event"))
                .isEqualTo(DiscordSummaryPublisher.DeliveryStatus.UNKNOWN);
    }
}
