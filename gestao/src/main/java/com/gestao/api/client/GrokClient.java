package com.gestao.api.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.gestao.api.config.lia.properties.LiaProperties;

/**
 * Único ponto de contato HTTP com a xAI. Não conhece ateliê, OS nem cliente.
 * Usa RestClient (síncrono honesto) em vez de WebClient+block.
 * Slug, temperatura e reasoning effort vêm de LiaProperties — nada hardcoded.
 */
@Component
public class GrokClient {

    private final RestClient restClient;
    private final LiaProperties props;

    public GrokClient(LiaProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("tool_choice", "auto");
        body.put("temperature", props.getTemperature());
        if (props.getReasoningEffort() != null && !props.getReasoningEffort().isBlank()) {
            body.put("reasoning_effort", props.getReasoningEffort());
        }

        return restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);
    }
}
