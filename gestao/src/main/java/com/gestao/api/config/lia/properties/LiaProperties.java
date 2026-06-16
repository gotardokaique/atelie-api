package com.gestao.api.config.lia.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração centralizada da Lia.
 * O slug do modelo NUNCA mais fica hardcoded no meio da lógica: vive aqui,
 * versionado em application.yml, com reasoning effort explícito por decisão.
 *
 * application.yml:
 *   lia:
 *     api-key: ${XAI_API_KEY}
 *     base-url: https://api.x.ai/v1
 *     model: grok-build-0.1
 *     reasoning-effort: low
 *     max-tool-iterations: 4
 *     temperature: 0.1
 *     janela-contexto: 10
 *     ttl-contexto: 24h
 */
@ConfigurationProperties(prefix = "lia")
public class LiaProperties {

    private String apiKey;
    private String baseUrl = "https://api.x.ai/v1";
    private String model = "grok-4.3";
    private String reasoningEffort = "low";
    private int maxToolIterations = 4;
    private double temperature = 0.1;
    private int janelaContexto = 20;
    private Duration ttlContexto = Duration.ofHours(24);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public int getMaxToolIterations() {
        return maxToolIterations;
    }

    public void setMaxToolIterations(int maxToolIterations) {
        this.maxToolIterations = maxToolIterations;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getJanelaContexto() {
        return janelaContexto;
    }

    public void setJanelaContexto(int janelaContexto) {
        this.janelaContexto = janelaContexto;
    }

    public Duration getTtlContexto() {
        return ttlContexto;
    }

    public void setTtlContexto(Duration ttlContexto) {
        this.ttlContexto = ttlContexto;
    }
}
