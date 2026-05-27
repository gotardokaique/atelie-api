package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gestao.api.entities.Insumo;
import com.gestao.api.enuns.UnidadeMedida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InsumoDTO(
        Long id,

        @NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotNull(message = "Unidade de medida é obrigatória")
        UnidadeMedida unidadeMedida,

        BigDecimal saldo,

        @PositiveOrZero(message = "Estoque mínimo deve ser zero ou positivo")
        BigDecimal estoqueMinimo,

        BigDecimal custoMedio,

        Boolean ativo,

        /** Derivado: true quando saldo <= estoqueMinimo (base do alerta). */
        Boolean abaixoMinimo
) {

    public static InsumoDTO convert(Insumo e) {
        if (e == null) {
            return null;
        }
        boolean abaixo = e.getSaldo() != null
                && e.getEstoqueMinimo() != null
                && e.getSaldo().compareTo(e.getEstoqueMinimo()) <= 0;

        return new InsumoDTO(
                e.getId(),
                e.getDescricao(),
                e.getUnidadeMedida(),
                e.getSaldo(),
                e.getEstoqueMinimo(),
                e.getCustoMedio(),
                e.isAtivo(),
                abaixo);
    }

    public static List<InsumoDTO> convert(List<Insumo> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<InsumoDTO> dtos = new ArrayList<>();
        for (Insumo e : entities) {
            if (e != null) {
                dtos.add(convert(e));
            }
        }
        return dtos;
    }
}
