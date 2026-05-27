package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Ajuste de inventário: informa a quantidade contada; o sistema posta a diferença. */
public record AjusteEstoqueDTO(
        @NotNull(message = "Quantidade contada é obrigatória")
        @PositiveOrZero(message = "Quantidade contada deve ser zero ou positiva")
        BigDecimal quantidadeContada,

        String observacao
) {}
