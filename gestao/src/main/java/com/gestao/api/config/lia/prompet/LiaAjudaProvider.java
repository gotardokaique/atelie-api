package com.gestao.api.config.lia.prompet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Fonte das seções de ajuda da Lia. Carrega o system-prompt-helper.txt uma vez,
 * quebra-o nas áreas marcadas (=== AREA: x === ... === FIM ===) e expõe o texto
 * de UMA área por vez — a tool explicar_ajuda devolve só a seção pedida ao modelo.
 *
 * Parse robusto: seção malformada é ignorada (loga warn). secao(area) inexistente
 * cai no fallback "geral"; se nem geral existir, devolve um texto mínimo seguro.
 */
@Component
public class LiaAjudaProvider {

    private static final Logger log = LoggerFactory.getLogger(LiaAjudaProvider.class);

    private static final String AREA_GERAL = "geral";

    /** === AREA: nome ===  (conteúdo)  === FIM === — nome no grupo 1, corpo no grupo 2. */
    private static final Pattern SECAO = Pattern.compile(
            "===\\s*AREA:\\s*(\\w+)\\s*===\\s*\\R(.*?)\\R?===\\s*FIM\\s*===",
            Pattern.DOTALL);

    private final Map<String, String> secoes;

    public LiaAjudaProvider() {
        this.secoes = parsear(load("lia/system-prompt-helper.txt"));
    }

    /**
     * Devolve o texto da área pedida. Área nula/desconhecida cai em "geral".
     * Nunca devolve o arquivo inteiro nem vazio.
     */
    public String secao(String area) {
        String chave = area == null ? "" : area.trim().toLowerCase();

        String texto = secoes.get(chave);
        if (texto != null) {
            return texto;
        }

        String geral = secoes.get(AREA_GERAL);
        if (geral != null) {
            log.warn("[Lia] Área de ajuda '{}' não encontrada; usando '{}'.", area, AREA_GERAL);
            return geral;
        }

        log.warn("[Lia] Helper de ajuda sem seções utilizáveis (área pedida: '{}').", area);
        return "Posso ajudar com serviços, clientes, financeiro, relatórios e estoque. "
                + "Sobre o que você quer saber?";
    }

    private Map<String, String> parsear(String conteudo) {
        Map<String, String> mapa = new HashMap<>();
        if (conteudo == null || conteudo.isBlank()) {
            log.warn("[Lia] Helper de ajuda vazio ou ausente.");
            return mapa;
        }

        Matcher m = SECAO.matcher(conteudo);
        while (m.find()) {
            String nome = m.group(1).trim().toLowerCase();
            String corpo = m.group(2).strip();
            if (nome.isBlank() || corpo.isBlank()) {
                log.warn("[Lia] Seção de ajuda malformada ignorada (nome='{}').", nome);
                continue;
            }
            mapa.put(nome, corpo);
        }

        if (mapa.isEmpty()) {
            log.warn("[Lia] Nenhuma seção de ajuda reconhecida no helper.");
        }
        return mapa;
    }

    private String load(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao carregar helper de ajuda da Lia: " + path, e);
        }
    }
}
