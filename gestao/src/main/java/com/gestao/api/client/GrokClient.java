package com.gestao.api.client;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.gestao.api.config.lia.properties.LiaProperties;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Component
public class GrokClient {

    private final RestClient restClient;
    private final LiaProperties props;

    public GrokClient(LiaProperties props) {
        this.props = props;

        // Timeouts explícitos: sem isto, uma degradação da xAI deixa cada chamada
        // pendurada indefinidamente, segurando a worker thread do Tomcat.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(props.getReadTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bulkhead(name = "grok")
    @CircuitBreaker(name = "grok")
    @Retry(name = "grok", fallbackMethod = "chatFallback")
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

    /**
     * Fallback do @Retry. Assinatura = parâmetros de chat() + o Throwable que disparou.
     * Traduz qualquer falha de resiliência numa exceção de domínio, que LiaOrchestrator
     * captura e converte em mensagem amigável.
     */
    @SuppressWarnings("unused")
    public Map<String, Object> chatFallback(List<Map<String, Object>> messages,
                                            List<Map<String, Object>> tools,
                                            Throwable t) {
        throw new GrokIndisponivelException(
                "Lia temporariamente indisponível (xAI/Grok degradada ou sobrecarregada).", t);
    }
}
