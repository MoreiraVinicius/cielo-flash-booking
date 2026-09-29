package com.cielo.flashbooking.event.summary;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DiscordSummaryMessageFormatter {

    private static final int MAX_LENGTH = 2_000;
    private static final String CAVEAT = "Reservas são temporárias; compras concluídas não são verificadas aqui.";

    public String format(String markdown) {
        if (markdown.length() <= 1_900) {
            return markdown;
        }

        List<String> required = new ArrayList<>();
        String section = "";
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("# ") && required.isEmpty()) {
                required.add(truncate(line, 220));
                continue;
            }
            if (required.size() == 1 && line.startsWith("Início:")) {
                required.add(truncate(line, 300));
                continue;
            }
            if (line.startsWith("## ")) {
                section = line;
                if (section.equals("## Resultado") || section.equals("## Ritmo")) {
                    required.add(section);
                }
                continue;
            }
            if ((section.equals("## Resultado") || section.equals("## Ritmo")) && !line.isBlank()) {
                required.add(truncate(line, 240));
            }
        }
        required.add(CAVEAT);
        String compact = String.join("\n", required);
        if (compact.length() <= MAX_LENGTH) {
            return compact;
        }
        String caveat = "\n" + CAVEAT;
        return truncate(compact, MAX_LENGTH - caveat.length()) + caveat;
    }

    private String truncate(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints - 1);
        return value.substring(0, end) + "…";
    }
}
