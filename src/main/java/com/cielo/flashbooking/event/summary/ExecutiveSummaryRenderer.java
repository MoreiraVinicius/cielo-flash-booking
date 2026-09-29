package com.cielo.flashbooking.event.summary;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ExecutiveSummaryRenderer {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
            .withZone(SAO_PAULO);

    public String render(EventSummaryFacts facts, OperationalSignalResult operationalSignals, String narrative) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(facts.eventName()).append('\n');
        markdown.append("Início: ")
                .append(format(facts.saleStartsAt()))
                .append(" | Fim: ")
                .append(format(facts.endsAt()))
                .append(" | Apurado: ")
                .append(format(facts.asOf()))
                .append(" | Capacidade: ")
                .append(facts.capacity())
                .append(" ingressos\n");
        markdown.append('\n');

        if (!facts.complete()) {
            return markdown.append("Apuração incompleta. Os números do resultado não puderam ser confirmados.\n")
                    .append("\nReservas são temporárias; compras concluídas não são verificadas aqui.\n")
                    .toString();
        }

        markdown.append("## Resultado\n");
        markdown.append(facts.acceptedTickets())
                .append(" ingressos passaram por ")
                .append(facts.acceptedReservations())
                .append(" reservas aceitas.\n");
        markdown.append(facts.validTicketsAtClose()).append(" ingressos ainda tinham reserva válida no fechamento.\n");
        if (facts.firstAvailableZeroAt() == null) {
            markdown.append("A disponibilidade não chegou a zero.\n");
        } else {
            Duration elapsed = Duration.between(facts.saleStartsAt(), facts.firstAvailableZeroAt());
            markdown.append("A disponibilidade chegou a zero pela primeira vez após ")
                    .append(formatDuration(elapsed))
                    .append(".\n");
        }
        if (facts.cancelledTicketsAtClose() > 0 || facts.expiredTicketsAtClose() > 0) {
            markdown.append(facts.cancelledTicketsAtClose())
                    .append(" ingressos tiveram reservas canceladas e ")
                    .append(facts.expiredTicketsAtClose())
                    .append(" venceram até o fechamento.\n");
        }

        markdown.append("\n## Ritmo\n");
        if (facts.acceptedReservations() == 0) {
            markdown.append("Nenhuma reserva foi aceita.\n");
        } else {
            markdown.append("O pico foi de ")
                    .append(facts.peakTickets())
                    .append(" ingressos reservados em um minuto.\n");
            if (facts.firstFiveMinuteTickets() != null) {
                long percentage = Math.round(facts.firstFiveMinuteTickets() * 100.0 / facts.acceptedTickets());
                markdown.append(percentage).append("% dos ingressos foram reservados nos primeiros cinco minutos.\n");
            }
        }
        if (narrative != null && !narrative.isBlank()) {
            markdown.append("\n## Leitura\n").append(narrative.trim()).append('\n');
        }
        appendOperationalSignals(markdown, operationalSignals);
        markdown.append("\nReservas são temporárias; compras concluídas não são verificadas aqui.\n");
        return markdown.toString();
    }

    private void appendOperationalSignals(StringBuilder markdown, OperationalSignalResult result) {
        if (result.status() == OperationalSignalResult.Status.EMPTY) {
            return;
        }
        markdown.append("\n## Operação\n");
        if (!result.signals().isEmpty()) {
            markdown.append("Sinais observados no ambiente; não há confirmação de relação com este evento.\n");
        }
        result.signals().stream()
                .sorted(java.util.Comparator.comparing(OperationalSignal::occurredAt))
                .limit(2)
                .forEach(signal -> markdown.append(signal.label())
                        .append(" às ")
                        .append(format(signal.occurredAt()))
                        .append(".\n"));
        if (result.status() == OperationalSignalResult.Status.PARTIAL
                || result.status() == OperationalSignalResult.Status.UNAVAILABLE) {
            markdown.append("Não foi possível verificar todos os alertas de infraestrutura.\n");
        }
    }

    private String format(Instant instant) {
        return DATE_TIME.format(instant);
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        if (seconds < 60) {
            return seconds + " segundos";
        }
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return remainder == 0 ? minutes + " minutos" : minutes + " minutos e " + remainder + " segundos";
    }
}
