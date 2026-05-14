package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gestao.api.entities.Despesa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DespesaDTO(
        Long id,

        @NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal valor,

        @NotNull(message = "Mês é obrigatório")
        Integer mes,

        @NotNull(message = "Ano é obrigatório")
        Integer ano
) {

    public static DespesaDTO convert(Despesa entity) {
        if (entity == null) return null;
        return new DespesaDTO(
                entity.getId(),
                entity.getDescricao(),
                entity.getValor(),
                entity.getMes(),
                entity.getAno()
        );
    }

    public static List<DespesaDTO> convert(List<Despesa> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        List<DespesaDTO> dtos = new ArrayList<>();
        for (Despesa entity : entities) {
            if (entity != null) dtos.add(DespesaDTO.convert(entity));
        }
        return dtos;
    }
}
