package com.gestao.api.config.lia.prompet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Carrega o template do system prompt uma vez e injeta a DATA ATUAL em runtime.
 * O .txt é editável sem recompilar; a data é resolvida a cada requisição
 * (carregar no construtor congelaria a data no dia do deploy).
 */
@Component
public class LiaPromptProvider {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String template;

    public LiaPromptProvider() {
        this.template = load("lia/system-prompt.txt");
    }

    public String systemPrompt() {
        LocalDate hoje = LocalDate.now();
        String diaSemana = hoje.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        return template
                .replace("{{HOJE}}", DATA_BR.format(hoje))
                .replace("{{DIA_SEMANA}}", diaSemana)
                .replace("{{ANO}}", String.valueOf(hoje.getYear()));
    }

    private String load(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao carregar prompt da Lia: " + path, e);
        }
    }
}