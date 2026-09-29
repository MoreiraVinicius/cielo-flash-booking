package com.cielo.flashbooking.event.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordSummaryMessageFormatterTest {

    private final DiscordSummaryMessageFormatter formatter = new DiscordSummaryMessageFormatter();

    @Test
    void longMessageDropsOptionalSectionsAndKeepsCommercialFactsAndCaveat() {
        String message = "# Evento\nInício: hoje\n\n## Resultado\nIngressos aceitos\nPrimeiro esgotamento\n"
                + "## Ritmo\nPico de reservas\n\n## Leitura\n" + "leitura ".repeat(300)
                + "\n## Operação\n" + "alerta ".repeat(100)
                + "\n\nReservas são temporárias; compras concluídas não são verificadas aqui.";

        String formatted = formatter.format(message);

        assertThat(formatted)
                .hasSizeLessThanOrEqualTo(2_000)
                .contains("## Resultado", "Ingressos aceitos", "Primeiro esgotamento", "## Ritmo", "Pico de reservas")
                .contains("Reservas são temporárias; compras concluídas não são verificadas aqui.")
                .doesNotContain("## Leitura", "## Operação");
    }
}
