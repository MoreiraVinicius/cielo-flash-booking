package com.cielo.flashbooking.event.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutiveSummaryRendererTest {

    private final ExecutiveSummaryRenderer renderer = new ExecutiveSummaryRenderer();

    @Test
    void rendersBusinessFactsAndTemporaryReservationCaveatInPlainPortuguese() {
        EventSummaryFacts facts = facts(true, 8, 12, 9, 2, 1, 2L, 6L);

        String markdown = renderer.render(
                facts,
                new OperationalSignalResult(OperationalSignalResult.Status.EMPTY, List.of()),
                "As reservas se concentraram no inicio do evento.");

        assertThat(markdown)
                .contains("Capacidade: 20 ingressos")
                .contains("12 ingressos passaram por 8 reservas aceitas")
                .contains("9 ingressos ainda tinham reserva válida no fechamento")
                .contains("A disponibilidade chegou a zero pela primeira vez após 2 minutos")
                .contains("2 ingressos tiveram reservas canceladas e 1 venceram até o fechamento")
                .contains("50% dos ingressos foram reservados nos primeiros cinco minutos")
                .contains("Início: 28/09/2026 09:00")
                .contains("Fim: 28/09/2026 09:30")
                .contains("Apurado: 28/09/2026 10:00")
                .contains("As reservas se concentraram no inicio do evento.")
                .contains("Reservas são temporárias; compras concluídas não são verificadas aqui.")
                .doesNotContain("Discord:", "SENT", "PENDING", "EXPIRED");
    }

    @Test
    void omitsAlertSectionWhenThereAreNoTransitionsAndExplainsPartialCoverage() {
        EventSummaryFacts facts = facts(true, 0, 0, 0, 0, 0, null, null);

        String empty = renderer.render(facts, OperationalSignalResult.empty(), null);
        String partial = renderer.render(
                facts, new OperationalSignalResult(OperationalSignalResult.Status.PARTIAL, List.of()), null);

        assertThat(empty)
                .contains("Nenhuma reserva foi aceita.")
                .doesNotContain("pico", "primeiros cinco minutos", "## Operação", "Leitura");
        assertThat(partial)
                .contains("## Operação")
                .contains("Não foi possível verificar todos os alertas de infraestrutura.")
                .doesNotContain("UNKNOWN", "NOT_CONFIGURED");
    }

    @Test
    void limitsOperationalNotesAndOmitsInconsistentBusinessNumbers() {
        EventSummaryFacts complete = facts(true, 1, 1, 1, 0, 0, 1L, 1L);
        OperationalSignalResult signals = new OperationalSignalResult(
                OperationalSignalResult.Status.OBSERVED,
                List.of(
                        new OperationalSignal("Worker sem tarefas ativas", Instant.parse("2026-09-28T12:01:00Z")),
                        new OperationalSignal("Fila com atraso", Instant.parse("2026-09-28T12:02:00Z")),
                        new OperationalSignal("Gateway com erros", Instant.parse("2026-09-28T12:03:00Z"))));
        String markdown = renderer.render(complete, signals, null);
        String incomplete = renderer.render(facts(false, 1, 3, 0, 0, 0, null, null), signals, "Inventado.");

        assertThat(markdown)
                .contains("Worker sem tarefas ativas", "Fila com atraso")
                .contains("Sinais observados no ambiente; não há confirmação de relação com este evento.")
                .doesNotContain("Gateway com erros", "FAILED");
        assertThat(incomplete)
                .contains("Apuração incompleta")
                .doesNotContain("3 ingressos", "## Ritmo", "Inventado.", "## Operação");
    }

    @Test
    void labelsTheSameOperationalAlertAsAnEnvironmentSignalForDifferentEvents() {
        OperationalSignalResult sharedSignal = new OperationalSignalResult(
                OperationalSignalResult.Status.OBSERVED,
                List.of(new OperationalSignal("Fila com atraso", Instant.parse("2026-09-28T12:03:00Z"))));

        String firstEvent = renderer.render(facts(true, 1, 1, 1, 0, 0, 1L, 1L), sharedSignal, null);
        String secondEvent = renderer.render(facts(true, 1, 1, 1, 0, 0, 1L, 1L), sharedSignal, null);

        assertThat(firstEvent)
                .contains("Sinais observados no ambiente; não há confirmação de relação com este evento.")
                .contains("Fila com atraso")
                .doesNotContain("causou");
        assertThat(secondEvent)
                .contains("Sinais observados no ambiente; não há confirmação de relação com este evento.")
                .contains("Fila com atraso")
                .doesNotContain("causou");
    }

    private EventSummaryFacts facts(
            boolean complete,
            long acceptedReservations,
            long acceptedTickets,
            long validAtClose,
            long cancelledAtClose,
            long expiredAtClose,
            Long peakTickets,
            Long firstFiveTickets) {
        Instant startsAt = Instant.parse("2026-09-28T12:00:00Z");
        return new EventSummaryFacts(
                java.util.UUID.randomUUID(),
                "Show de teste",
                20,
                startsAt,
                startsAt.plusSeconds(1800),
                acceptedReservations,
                acceptedTickets,
                validAtClose,
                cancelledAtClose,
                expiredAtClose,
                acceptedReservations == 0 ? null : startsAt,
                peakTickets,
                firstFiveTickets,
                acceptedReservations == 0 ? null : startsAt.plusSeconds(120),
                startsAt.plusSeconds(3600),
                complete);
    }
}
