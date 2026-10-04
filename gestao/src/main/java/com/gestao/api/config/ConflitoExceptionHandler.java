package com.gestao.api.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gen.core.api.ApiResponse;
import com.gestao.api.services.exceptions.TelefoneJaCadastradoException;

/** Roda antes do GlobalExceptionHandler do core, que trataria a exceção como 422. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConflitoExceptionHandler {

    @ExceptionHandler(TelefoneJaCadastradoException.class)
    public ResponseEntity<ApiResponse<Void>> handleTelefoneJaCadastrado(TelefoneJaCadastradoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
