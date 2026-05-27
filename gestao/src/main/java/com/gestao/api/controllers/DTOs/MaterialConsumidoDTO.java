package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Material consumido numa peça. Sai da explosão da ficha técnica (sugestão) e é
 * editável pela usuária antes de finalizar — é o que de fato vira SAIDA.
 */
public record MaterialConsumidoDTO(
        @NotNull(message = "Insumo é obrigatório")
        Long insumoId,

        String insumoDescricao,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser positiva")
        BigDecimal quantidade
) {}
