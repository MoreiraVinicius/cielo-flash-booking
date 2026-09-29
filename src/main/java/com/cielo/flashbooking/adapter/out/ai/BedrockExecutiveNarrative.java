package com.cielo.flashbooking.adapter.out.ai;

import com.cielo.flashbooking.event.summary.EventSummaryFacts;
import com.cielo.flashbooking.event.summary.ExecutiveNarrative;
import com.cielo.flashbooking.event.summary.ExecutiveSummaryProperties;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import tools.jackson.databind.json.JsonMapper;

@Component
class BedrockExecutiveNarrative implements ExecutiveNarrative {

    private static final Logger LOGGER = LoggerFactory.getLogger(BedrockExecutiveNarrative.class);
    private static final int MAX_PROMPT_BYTES = 2_048;
    private static final int MAX_OUTPUT_TOKENS = 120;
    private static final String INSTRUCTIONS = """
            Escreva em português simples no máximo duas frases curtas interpretando somente o ritmo agregado das reservas temporárias.
            Não repita valores numéricos, datas ou horários. Não afirme compra, pagamento, receita ou causa de incidente.
            Não inclua nomes de clientes, eventos, alarmes, serviços ou termos técnicos. Se não houver leitura clara, responda vazio.
            Dados agregados:
            """;

    private final BedrockRuntimeClient bedrockClient;
    private final ExecutiveSummaryProperties properties;
    private final JsonMapper jsonMapper;

    BedrockExecutiveNarrative(
            BedrockRuntimeClient bedrockClient, ExecutiveSummaryProperties properties, JsonMapper jsonMapper) {
        this.bedrockClient = bedrockClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<Narrative> write(EventSummaryFacts facts) {
        if (!facts.complete() || facts.acceptedTickets() == 0) {
            return Optional.empty();
        }

        String prompt;
        try {
            prompt = INSTRUCTIONS + jsonMapper.writeValueAsString(allowedAggregates(facts));
        } catch (Exception exception) {
            LOGGER.warn(
                    "Executive summary narrative unavailable: {}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
        if (prompt.getBytes(StandardCharsets.UTF_8).length > MAX_PROMPT_BYTES) {
            return Optional.empty();
        }

        try {
            var response = bedrockClient.converse(ConverseRequest.builder()
                    .modelId(properties.getBedrock().getModelId())
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.builder().text(prompt).build())
                            .build())
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(MAX_OUTPUT_TOKENS)
                            .temperature(0.1f)
                            .build())
                    .build());
            String text = response.output().message().content().stream()
                    .map(ContentBlock::text)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("")
                    .trim();
            int inputTokens = response.usage() == null || response.usage().inputTokens() == null
                    ? -1
                    : response.usage().inputTokens();
            int outputTokens = response.usage() == null || response.usage().outputTokens() == null
                    ? -1
                    : response.usage().outputTokens();

            if (!isAcceptable(text) || inputTokens < 0 || outputTokens < 0 || outputTokens > MAX_OUTPUT_TOKENS) {
                return Optional.empty();
            }
            return Optional.of(new Narrative(text, properties.getBedrock().getModelId(), inputTokens, outputTokens));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Executive summary narrative unavailable: {}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Map<String, Object> allowedAggregates(EventSummaryFacts facts) {
        Map<String, Object> values = new HashMap<>();
        values.put("acceptedReservations", facts.acceptedReservations());
        values.put("acceptedTickets", facts.acceptedTickets());
        values.put("validTicketsAtClose", facts.validTicketsAtClose());
        values.put("cancelledTicketsAtClose", facts.cancelledTicketsAtClose());
        values.put("expiredTicketsAtClose", facts.expiredTicketsAtClose());
        values.put("peakTicketsPerMinute", facts.peakTickets());
        values.put("firstFiveMinuteTickets", facts.firstFiveMinuteTickets());
        return values;
    }

    private boolean isAcceptable(String text) {
        if (text.isBlank() || text.length() > 1_000 || text.matches("(?s).*\\d.*")) {
            return false;
        }
        if (text.matches(
                "(?is).*(compr\\w*|venda\\w*|pagamento\\w*|receita|convers[aã]o|alerta\\w*|incidente\\w*|falha\\w*|erro\\w*|indispon[ií]vel|por causa|por conta|em raz[aã]o|devido\\s+a|caus\\w*|provoc\\w*|resultado\\s+de).*")) {
            return false;
        }
        String[] sentences = text.split("(?<=[.!?])\\s+");
        return sentences.length <= 2;
    }
}
