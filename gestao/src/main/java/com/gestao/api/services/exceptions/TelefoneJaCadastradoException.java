package com.gestao.api.services.exceptions;

import com.gen.core.security.exception.BusinessException;

/**
 * Telefone já usado por outro cliente do mesmo usuário. Vira HTTP 409 pelo
 * {@code ConflitoExceptionHandler}; por estender {@link BusinessException},
 * também dispara rollback da transação.
 */
public class TelefoneJaCadastradoException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public TelefoneJaCadastradoException() {
        super("Já existe um cliente cadastrado com este número de telefone.");
    }
}
