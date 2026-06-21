package com.gestao.api.services.exceptions;

import com.gen.core.security.exception.BusinessException;

public class EstoqueInsuficienteException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public EstoqueInsuficienteException(String message) {
        super(message);
    }
}
