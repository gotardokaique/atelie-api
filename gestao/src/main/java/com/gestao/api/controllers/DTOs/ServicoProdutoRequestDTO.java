package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Vincula um produto do catálogo a um serviço. */
public record ServicoProdutoRequestDTO(
        @NotNull(message = "Produto é obrigatório")
        Long produtoId,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser positiva")
        BigDecimal quantidade
) {}
