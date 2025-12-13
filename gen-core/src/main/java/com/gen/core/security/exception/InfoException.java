package com.gen.core.security.exception;

public class InfoException extends RuntimeException {

    private final String infoMessage;

    public InfoException(String infoMessage) {
        super(infoMessage);
        this.infoMessage = infoMessage;
    }

    public String getInfoMessage() {
        return infoMessage;
    }
}
