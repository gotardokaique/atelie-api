package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gestao.api.entities.FichaTecnica;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Um item da ficha técnica: insumo + quantidade sugerida por unidade do produto. */
public record FichaTecnicaItemDTO(
        @NotNull(message = "Insumo é obrigatório")
        Long insumoId,

        String insumoDescricao,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser positiva")
        BigDecimal quantidade
) {

    public static FichaTecnicaItemDTO convert(FichaTecnica f) {
        if (f == null) {
            return null;
        }
        return new FichaTecnicaItemDTO(
                f.getInsumo() != null ? f.getInsumo().getId() : null,
                f.getInsumo() != null ? f.getInsumo().getDescricao() : null,
                f.getQuantidade());
    }

    public static List<FichaTecnicaItemDTO> convert(List<FichaTecnica> fichas) {
        if (fichas == null || fichas.isEmpty()) {
            return Collections.emptyList();
        }
        List<FichaTecnicaItemDTO> dtos = new ArrayList<>();
        for (FichaTecnica f : fichas) {
            if (f != null) {
                dtos.add(convert(f));
            }
        }
        return dtos;
    }
}
