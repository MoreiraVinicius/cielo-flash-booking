package com.cielo.flashbooking.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cielo.flashbooking.event.summary.EventSummaryFacts;
import com.cielo.flashbooking.event.summary.ExecutiveSummaryProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import tools.jackson.databind.json.JsonMapper;

class BedrockExecutiveNarrativeTest {

    private final BedrockRuntimeClient bedrockClient = mock(BedrockRuntimeClient.class);
    private final ExecutiveSummaryProperties properties = new ExecutiveSummaryProperties();
    private final BedrockExecutiveNarrative narrative = new BedrockExecutiveNarrative(
            bedrockClient, properties, JsonMapper.builder().build());

    @Test
    void sendsOnlyAllowedAggregatesAndRequestsOneBoundedLowRandomnessResponse() {
        when(bedrockClient.converse(any(ConverseRequest.class)))
                .thenReturn(response("As reservas se concentraram no inicio, e o ritmo diminuiu mais tarde.", 80));

        var result = narrative.write(facts(true, 4));

        ArgumentCaptor<ConverseRequest> request = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(bedrockClient).converse(request.capture());
        String prompt =
                request.getValue().messages().getFirst().content().getFirst().text();
        assertThat(request.getValue().modelId()).isEqualTo("amazon.nova-micro-v1:0");
        assertThat(request.getValue().inferenceConfig().maxTokens()).isEqualTo(120);
        assertThat(request.getValue().inferenceConfig().temperature()).isEqualTo(0.1f);
        assertThat(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(2_048);
        assertThat(prompt)
                .contains("acceptedReservations", "acceptedTickets", "validTicketsAtClose")
                .doesNotContain("customer@example.com", "Private Event Name", "alarm-name", "raw log");
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().outputTokens()).isEqualTo(80);
    }

    @Test
    void rejectsDigitsPurchaseClaimsCausalClaimsAndLongResponses() {
        for (String text : List.of(
                "Foram reservados 12 ingressos.",
                "As compras foram confirmadas.",
                "O alerta causou a queda das reservas.",
                "O ritmo mudou devido a falhas no sistema.",
                "Primeira frase. Segunda frase. Terceira frase.")) {
            when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(response(text, 30));
            assertThat(narrative.write(facts(true, 1))).isEmpty();
        }

        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(response("Texto curto e seguro.", 121));
        assertThat(narrative.write(facts(true, 1))).isEmpty();
    }

    @Test
    void skipsInferenceForIncompleteFactsOrNoAcceptedReservations() {
        assertThat(narrative.write(facts(false, 1))).isEmpty();
        assertThat(narrative.write(facts(true, 0))).isEmpty();
        verify(bedrockClient, never()).converse(any(ConverseRequest.class));
    }

    private ConverseResponse response(String text, int outputTokens) {
        var message = Message.builder()
                .content(ContentBlock.builder().text(text).build())
                .build();
        var output = ConverseOutput.builder().message(message).build();
        var usage = TokenUsage.builder()
                .inputTokens(50)
                .outputTokens(outputTokens)
                .totalTokens(50 + outputTokens)
                .build();
        return ConverseResponse.builder().output(output).usage(usage).build();
    }

    private EventSummaryFacts facts(boolean complete, long acceptedReservations) {
        Instant start = Instant.parse("2026-09-28T12:00:00Z");
        return new EventSummaryFacts(
                java.util.UUID.randomUUID(),
                "Private Event Name",
                20,
                start,
                start.plusSeconds(1800),
                acceptedReservations,
                acceptedReservations == 0 ? 0 : 4,
                acceptedReservations == 0 ? 0 : 2,
                1,
                1,
                start.plusSeconds(120),
                3L,
                2L,
                start.plusSeconds(600),
                start.plusSeconds(3600),
                complete);
    }
}
