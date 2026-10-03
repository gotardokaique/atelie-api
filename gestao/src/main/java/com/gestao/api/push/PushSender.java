package com.gestao.api.push;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Envio de push sem dependência do domínio. Hoje a implementação é a Expo Push API;
 * se outro produto precisar, este pacote pode ir para o gen-core como está.
 */
public interface PushSender {

    /** Um ticket por mensagem, na mesma ordem. Nunca lança: falhas voltam como ticket de erro. */
    List<PushTicket> enviar(List<PushMessage> mensagens);

    /** Recibos dos tickets já processados pelo provedor; ids ainda pendentes não aparecem no mapa. */
    Map<String, PushReceipt> consultarRecibos(Collection<String> ticketIds);
}
