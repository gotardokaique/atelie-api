package com.gestao.api.client;

/**
 * Sinaliza que a xAI/Grok está indisponível ou degradada além do que a resiliência
 * tolera: timeouts esgotaram os retries, o circuit breaker está aberto, ou o bulkhead
 * está cheio (fail-fast). Lançada pelo fallback do {@link GrokClient}.
 *
 * É capturada em LiaOrchestrator.conversar() (catch genérico de Exception), que devolve
 * mensagem amigável ao usuário em vez de pendurar a worker thread do Tomcat.
 */
public class GrokIndisponivelException extends RuntimeException {

    public GrokIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
