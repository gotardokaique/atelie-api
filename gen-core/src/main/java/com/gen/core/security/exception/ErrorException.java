package com.gen.core.security.exception;

public class ErrorException extends RuntimeException {

    private final String errorMessage;

    public ErrorException(String errorMessage) {
        super(errorMessage);
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
