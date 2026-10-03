package com.gestao.api.push;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Expo Push API (https://docs.expo.dev/push-notifications/sending-notifications/).
 * Exige EXPO_ACCESS_TOKEN porque o projeto usa "Enhanced Security for Push".
 */
@Component
public class ExpoPushClient implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);

    static final int LOTE_ENVIO = 100;
    static final int LOTE_RECIBOS = 1000;
    static final String ERRO_SEM_TOKEN = "ExpoAccessTokenAusente";
    static final String ERRO_REQUISICAO = "FalhaRequisicao";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String accessToken;

    public ExpoPushClient(@Value("${EXPO_ACCESS_TOKEN:}") String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = RestClient.builder()
                .baseUrl("https://exp.host/--/api/v2/push")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        if (this.accessToken.isEmpty()) {
            log.error("[PUSH] EXPO_ACCESS_TOKEN não configurado: nenhum push será enviado "
                    + "(as notificações continuam sendo salvas e aparecem no sininho).");
        }
    }

    @Override
    public List<PushTicket> enviar(List<PushMessage> mensagens) {
        List<PushTicket> tickets = new ArrayList<>(mensagens.size());
        if (mensagens.isEmpty()) {
            return tickets;
        }
        if (accessToken.isEmpty()) {
            log.error("[PUSH] Envio de {} push(es) abortado: EXPO_ACCESS_TOKEN não configurado.", mensagens.size());
            mensagens.forEach(m -> tickets.add(PushTicket.falha(m.to(), ERRO_SEM_TOKEN,
                    "EXPO_ACCESS_TOKEN não configurado")));
            return tickets;
        }

        for (int inicio = 0; inicio < mensagens.size(); inicio += LOTE_ENVIO) {
            List<PushMessage> lote = mensagens.subList(inicio, Math.min(inicio + LOTE_ENVIO, mensagens.size()));
            tickets.addAll(enviarLote(lote));
        }
        return tickets;
    }

    @Override
    public Map<String, PushReceipt> consultarRecibos(Collection<String> ticketIds) {
        Map<String, PushReceipt> recibos = new HashMap<>();
        if (ticketIds.isEmpty() || accessToken.isEmpty()) {
            return recibos;
        }

        List<String> ids = new ArrayList<>(ticketIds);
        for (int inicio = 0; inicio < ids.size(); inicio += LOTE_RECIBOS) {
            List<String> lote = ids.subList(inicio, Math.min(inicio + LOTE_RECIBOS, ids.size()));
            try {
                Map<String, Object> resposta = restClient.post()
                        .uri("/getReceipts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .body(Map.of("ids", lote))
                        .retrieve()
                        .body(JSON);

                Object data = resposta == null ? null : resposta.get("data");
                if (data instanceof Map<?, ?> porId) {
                    porId.forEach((id, valor) -> recibos.put(String.valueOf(id), lerRecibo(String.valueOf(id), valor)));
                }
            } catch (Exception e) {
                log.warn("[PUSH] Falha ao consultar {} recibo(s): {}", lote.size(), e.getMessage());
            }
        }
        return recibos;
    }

    private List<PushTicket> enviarLote(List<PushMessage> lote) {
        List<Map<String, Object>> corpo = lote.stream().map(ExpoPushClient::paraJson).toList();
        try {
            Map<String, Object> resposta = restClient.post()
                    .uri("/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(corpo)
                    .retrieve()
                    .body(JSON);

            Object data = resposta == null ? null : resposta.get("data");
            if (data instanceof List<?> itens && itens.size() == lote.size()) {
                List<PushTicket> tickets = new ArrayList<>(lote.size());
                for (int i = 0; i < lote.size(); i++) {
                    tickets.add(lerTicket(lote.get(i).to(), itens.get(i)));
                }
                return tickets;
            }
            log.warn("[PUSH] Resposta inesperada da Expo para lote de {}: {}", lote.size(), resposta);
            return falharLote(lote, "Resposta inesperada da Expo");
        } catch (Exception e) {
            log.warn("[PUSH] Falha ao enviar lote de {} push(es): {}", lote.size(), e.getMessage());
            return falharLote(lote, e.getMessage());
        }
    }

    private static List<PushTicket> falharLote(List<PushMessage> lote, String mensagem) {
        return lote.stream().map(m -> PushTicket.falha(m.to(), ERRO_REQUISICAO, mensagem)).toList();
    }

    private static Map<String, Object> paraJson(PushMessage m) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("to", m.to());
        json.put("title", m.title());
        json.put("body", m.body());
        json.put("data", m.data() == null ? Map.of() : m.data());
        json.put("sound", "default");
        json.put("priority", "high");
        if (m.channelId() != null) {
            json.put("channelId", m.channelId());
        }
        return json;
    }

    static PushTicket lerTicket(String token, Object item) {
        if (item instanceof Map<?, ?> ticket) {
            if ("ok".equals(ticket.get("status"))) {
                return PushTicket.ok(token, String.valueOf(ticket.get("id")));
            }
            return PushTicket.falha(token, codigoErro(ticket), String.valueOf(ticket.get("message")));
        }
        return PushTicket.falha(token, ERRO_REQUISICAO, "Ticket ilegível");
    }

    static PushReceipt lerRecibo(String id, Object valor) {
        if (valor instanceof Map<?, ?> recibo && "ok".equals(recibo.get("status")) == false) {
            return new PushReceipt(id, codigoErro(recibo), String.valueOf(recibo.get("message")));
        }
        return new PushReceipt(id, null, null);
    }

    private static String codigoErro(Map<?, ?> item) {
        if (item.get("details") instanceof Map<?, ?> details && details.get("error") != null) {
            return String.valueOf(details.get("error"));
        }
        return "Erro";
    }
}
