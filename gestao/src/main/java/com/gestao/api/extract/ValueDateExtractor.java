package com.gestao.api.extract;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Extração DETERMINÍSTICA de valor e data — usada apenas como FALLBACK quando o
 * modelo deixou o campo tipado vazio. Não é uma segunda fonte de verdade
 * competindo com o prompt: o modelo é a fonte primária, isto é a rede de segurança.
 *
 * Corrige o bug do código antigo no cálculo de dia da semana, agora resolvido
 * com TemporalAdjusters.next() — uma linha, correto para todos os 7 dias.
 */
@Component
public class ValueDateExtractor {

    private static final Pattern[] VALOR_PATTERNS = {
            Pattern.compile("(?i)\\bvalor\\s+([\\d.,]+)"),
            Pattern.compile("(?i)r\\$\\s*([\\d.,]+)"),
            Pattern.compile("(?i)\\b([\\d.,]+)\\s+reais?\\b"),
            Pattern.compile("(?i)\\b(?:custa|por|cobrar|cobra)\\s+([\\d.,]+)"),
    };

    private static final Pattern DATA_NUMERICA =
            Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b");

    private static final Pattern EM_X_DIAS =
            Pattern.compile("(?i)\\bem\\s+(\\d+)\\s+dias?\\b");

    private static final Map<String, DayOfWeek> DIAS = Map.ofEntries(
            Map.entry("domingo", DayOfWeek.SUNDAY),
            Map.entry("segunda", DayOfWeek.MONDAY),
            Map.entry("terça", DayOfWeek.TUESDAY),
            Map.entry("terca", DayOfWeek.TUESDAY),
            Map.entry("quarta", DayOfWeek.WEDNESDAY),
            Map.entry("quinta", DayOfWeek.THURSDAY),
            Map.entry("sexta", DayOfWeek.FRIDAY),
            Map.entry("sábado", DayOfWeek.SATURDAY),
            Map.entry("sabado", DayOfWeek.SATURDAY));

    public Optional<BigDecimal> extrairValor(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        for (Pattern p : VALOR_PATTERNS) {
            Matcher m = p.matcher(texto);
            if (m.find()) {
                String raw = normalizarNumero(m.group(1));
                try {
                    return Optional.of(new BigDecimal(raw));
                } catch (NumberFormatException ignored) {
                    // tenta o próximo padrão
                }
            }
        }
        return Optional.empty();
    }

    public Optional<LocalDate> extrairData(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        LocalDate hoje = LocalDate.now();
        String lower = texto.toLowerCase();

        // 1. DD/MM ou DD/MM/AAAA
        Matcher numerica = DATA_NUMERICA.matcher(texto);
        if (numerica.find()) {
            Optional<LocalDate> data = parseDataNumerica(numerica, hoje);
            if (data.isPresent()) {
                return data;
            }
        }

        // 2. amanhã / hoje
        if (lower.contains("amanhã") || lower.contains("amanha")) {
            return Optional.of(hoje.plusDays(1));
        }
        if (lower.contains("hoje")) {
            return Optional.of(hoje);
        }

        // 3. em X dias
        Matcher emDias = EM_X_DIAS.matcher(lower);
        if (emDias.find()) {
            return Optional.of(hoje.plusDays(Integer.parseInt(emDias.group(1))));
        }

        // 4. dia da semana (próxima [dia]) — CORRETO via TemporalAdjusters
        for (Map.Entry<String, DayOfWeek> e : DIAS.entrySet()) {
            if (lower.contains(e.getKey())) {
                return Optional.of(hoje.with(TemporalAdjusters.next(e.getValue())));
            }
        }

        return Optional.empty();
    }

    private Optional<LocalDate> parseDataNumerica(Matcher m, LocalDate hoje) {
        try {
            int dia = Integer.parseInt(m.group(1));
            int mes = Integer.parseInt(m.group(2));
            int ano;
            if (m.group(3) != null) {
                ano = normalizarAno(Integer.parseInt(m.group(3)));
            } else {
                ano = hoje.getYear();
                LocalDate candidata = LocalDate.of(ano, mes, dia);
                if (candidata.isBefore(hoje)) {
                    ano++;
                }
            }
            return Optional.of(LocalDate.of(ano, mes, dia));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private int normalizarAno(int ano) {
        return ano < 100 ? 2000 + ano : ano;
    }

    /**
     * Normaliza número PT-BR para BigDecimal.
     * "1.500,50" → "1500.50" ; "200" → "200" ; "150,50" → "150.50"
     */
    private String normalizarNumero(String raw) {
        String s = raw.trim();
        if (s.contains(",")) {
            // vírgula é decimal; ponto é separador de milhar
            s = s.replace(".", "").replace(",", ".");
        }
        return s;
    }
}
