package com.gestao.api.services.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(NotFoundException.class);

    public NotFoundException(String message) {
        super(message);
        log.warn(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
        log.warn(message, cause);
    }
}
