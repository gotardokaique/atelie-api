package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gestao.api.entities.Estoque;

import jakarta.validation.constraints.PositiveOrZero;

public record EstoqueDTO(
        Long id,
        String nomeItem,

        @PositiveOrZero(message = "Valor gasto deve ser zero ou positivo")
        BigDecimal valorGasto,

        @PositiveOrZero(message = "Quantidade deve ser zero ou positiva")
        Integer quantidadeComprada
) {

    public static EstoqueDTO convert(Estoque entity) {
        if (entity == null) {
            return null;
        }
        return new EstoqueDTO(
                entity.getId(),
                entity.getNomeItem(),
                entity.getValorGasto(),
                entity.getQuantidadeComprada()
        );
    }

    public static List<EstoqueDTO> convert(List<Estoque> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<EstoqueDTO> dtos = new ArrayList<>();

        for (Estoque entity : entities) {
            if (entity == null) {
                continue;
            }
            dtos.add(EstoqueDTO.convert(entity));
        }

        return dtos;
    }
}
