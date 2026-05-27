package com.gestao.api.services.exceptions;

/**
 * Lançada quando uma SAIDA tenta baixar mais do que o saldo disponível.
 * Estende {@link BusinessException}, então é tratada como 400 pelo
 * {@code ResourceExceptionHandler} e dispara rollback da transação.
 */
public class EstoqueInsuficienteException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public EstoqueInsuficienteException(String message) {
        super(message);
    }
}
