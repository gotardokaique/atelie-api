package com.gestao.api.config.lia.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestao.api.config.lia.properties.LiaProperties;

/**
 * Persistência da janela de contexto conversacional da Lia em Redis.
 *
 * Guarda apenas o essencial — a fala do usuário e o texto final do assistant —
 * numa lista por usuário, limitada às últimas N mensagens e com expiração por
 * inatividade. Todas as operações são best-effort: se o Redis estiver fora do ar,
 * o método loga e segue, NUNCA propaga exceção para o fluxo da conversa.
 */
@Service
public class LiaContextoService {

    private static final Logger log = LoggerFactory.getLogger(LiaContextoService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LiaProperties props;

    public LiaContextoService(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              LiaProperties props) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    private String chave(Long sitId, Long usuId) {
        return "lia:ctx:" + sitId + ":" + usuId;
    }

    /**
     * Anexa uma mensagem à janela do usuário, mantém só as últimas N e renova o TTL.
     * Best-effort: falha de Redis apenas loga um warn.
     */
    public void registrar(Long sitId, Long usuId, String role, String conteudo) {
        try {
            String chave = chave(sitId, usuId);
            String json = objectMapper.writeValueAsString(Map.of("role", role, "content", conteudo));

            redisTemplate.opsForList().rightPush(chave, json);
            redisTemplate.opsForList().trim(chave, -props.getJanelaContexto(), -1);
            redisTemplate.expire(chave, props.getTtlContexto());
        } catch (Exception e) {
            log.warn("[Lia] Não foi possível registrar contexto: {}", e.getMessage());
        }
    }

    /**
     * Devolve a janela atual em ordem cronológica. Itens corrompidos são ignorados.
     * Best-effort: retorna lista vazia se não houver nada ou em caso de erro.
     */
    public List<Map<String, Object>> janela(Long sitId, Long usuId) {
        try {
            List<String> itens = redisTemplate.opsForList().range(chave(sitId, usuId), 0, -1);
            if (itens == null || itens.isEmpty()) {
                return List.of();
            }

            List<Map<String, Object>> janela = new ArrayList<>(itens.size());
            for (String item : itens) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mensagem = objectMapper.readValue(item, Map.class);
                    janela.add(mensagem);
                } catch (Exception e) {
                    log.warn("[Lia] Item de contexto corrompido ignorado: {}", e.getMessage());
                }
            }
            return janela;
        } catch (Exception e) {
            log.warn("[Lia] Não foi possível ler contexto: {}", e.getMessage());
            return List.of();
        }
    }
}
