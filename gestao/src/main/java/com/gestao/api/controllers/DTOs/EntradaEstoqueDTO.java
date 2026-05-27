package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Entrada de insumo (compra). Recalcula o custo médio ponderado. */
public record EntradaEstoqueDTO(
        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser positiva")
        BigDecimal quantidade,

        @NotNull(message = "Custo unitário é obrigatório")
        @PositiveOrZero(message = "Custo unitário deve ser zero ou positivo")
        BigDecimal custoUnitario,

        String observacao
) {}
