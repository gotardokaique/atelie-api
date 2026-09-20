package com.gestao.api.client;

public class GrokIndisponivelException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public GrokIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
