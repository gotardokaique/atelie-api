package com.gestao.api.config.lia.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.gen.core.security.SessionService;
import com.gestao.api.config.lia.properties.LiaProperties;

@Service
public class LiaContextoService {

    private static final Logger log = LoggerFactory.getLogger(LiaContextoService.class);

    private final SessionService session;
    private final ObjectMapper objectMapper;
    private final LiaProperties props;

    public LiaContextoService(SessionService session,
                              ObjectMapper objectMapper,
                              LiaProperties props) {
        this.session = session;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    private String chave(Long sitId, Long usuId) {
        return "lia:ctx:" + sitId + ":" + usuId;
    }

    public void registrar(Long sitId, Long usuId, String role, String conteudo) {
        try {
            String chave = chave(sitId, usuId);

            List<String> itens = lerLista(chave);
            itens.add(objectMapper.writeValueAsString(Map.of("role", role, "content", conteudo)));

            int janela = props.getJanelaContexto();
            if (itens.size() > janela) {
                itens = new ArrayList<>(itens.subList(itens.size() - janela, itens.size()));
            }

            session.put(chave, objectMapper.writeValueAsString(itens),
                        props.getTtlContexto().toSeconds());
        } catch (Exception e) {
            log.warn("[Lia] Não foi possível registrar contexto: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> janela(Long sitId, Long usuId) {
        try {
            List<String> itens = lerLista(chave(sitId, usuId));
            if (itens.isEmpty()) {
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

    private List<String> lerLista(String chave) {
        String json = session.get(chave);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}