package com.gestao.api.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // Para ler de application.properties
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate; // Cliente HTTP do Spring

public class WhatsAppNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);

    private final RestTemplate restTemplate;

    @Value("${whatsapp.api.url}") 
    private String apiUrl;

    @Value("${whatsapp.api.token}")
    private String apiToken;

    @Value("${whatsapp.api.template.name}") 
    private String templateName;

    public WhatsAppNotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void enviarMensagemServicoFinalizado(String numeroTelefone, String nomeCliente, Long servicoId) {
        if (numeroTelefone == null || numeroTelefone.trim().isEmpty()) {
            log.warn("Número de telefone não fornecido para o cliente {} do serviço ID {}. Notificação não enviada.", nomeCliente, servicoId);
            return;
        }

          String telefoneFormatado = formatarTelefoneE164(numeroTelefone);
        if (telefoneFormatado == null) {
            log.warn("Número de telefone inválido '{}' para o cliente {}. Notificação não enviada.", numeroTelefone, nomeCliente);
            return;
        }

          String mensagemTexto = String.format(
            "Olá, %s! Seu serviço de costura #%d em nosso ateliê foi concluído e está pronto para retirada. Obrigado!",
            nomeCliente,
            servicoId
        );
          
       String requestBodyJson = String.format("""
            {
                "messaging_product": "whatsapp",
                "to": "%s",
                "type": "template",
                "template": {
                    "name": "%s",
                    "language": {
                        "code": "pt_BR"
                    },
                    "components": [
                        {
                            "type": "body",
                            "parameters": [
                                { "type": "text", "text": "%s" }, // Variável 1 no template (nome do cliente)
                                { "type": "text", "text": "%d" }  // Variável 2 no template (ID do serviço)
                            ]
                        }
                        // Você pode ter componentes de header, buttons, etc.
                    ]
                }
            }
            """, telefoneFormatado, templateName, nomeCliente, servicoId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken); // Ou headers.set("Authorization", "Bearer " + apiToken);

        HttpEntity<String> entity = new HttpEntity<>(requestBodyJson, headers); // Use requestBodyJson ou requestBodyJsonSimpleText

        try {
            log.info("Enviando mensagem WhatsApp para: {} (Serviço ID: {})", telefoneFormatado, servicoId);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Mensagem WhatsApp enviada com sucesso para {}. Resposta: {}", telefoneFormatado, response.getBody());
            } else {
                log.error("Falha ao enviar mensagem WhatsApp para {}. Status: {}, Resposta: {}",
                    telefoneFormatado, response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem WhatsApp para {}: {}", telefoneFormatado, e.getMessage(), e);
        }
    }

    private String formatarTelefoneE164(String telefone) {
        String numeros = telefone.replaceAll("[^0-9]", "");

         if (numeros.length() == 10) { 
            return "55" + numeros; 
        } else if (numeros.length() == 11 && numeros.startsWith("0")) { // Ex: 0119XXXXXXXX
             return "55" + numeros.substring(1);
        } else if (numeros.length() == 11) { // Ex: 119XXXXXXXX
            return "55" + numeros;
        } else if (numeros.length() == 12 && numeros.startsWith("55") && numeros.charAt(4) != '9') { // Ex: 5511XXXXXXXX (sem 9º dígito SP)
       return numeros;
        } else if (numeros.length() == 13 && numeros.startsWith("55")) { // Ex: 55119XXXXXXXX
            return numeros;
        }
        log.warn("Formato de telefone não reconhecido para conversão E.164: {}", telefone);
        return null; 
    }
}
