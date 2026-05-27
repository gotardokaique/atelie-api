package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gestao.api.entities.Produto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProdutoDTO(
        Long id,

        @NotBlank(message = "Descrição é obrigatória")
        String descricao,

        @NotNull(message = "Preço de venda é obrigatório")
        @PositiveOrZero(message = "Preço de venda deve ser zero ou positivo")
        BigDecimal precoVenda,

        Boolean ativo,

        /** Ficha técnica (receita de insumos). Opcional na criação, editável no update. */
        @Valid
        List<FichaTecnicaItemDTO> ficha
) {

    /** Converte sem a ficha (para listagens). */
    public static ProdutoDTO convert(Produto p) {
        if (p == null) {
            return null;
        }
        return new ProdutoDTO(
                p.getId(),
                p.getDescricao(),
                p.getPrecoVenda(),
                p.isAtivo(),
                Collections.emptyList());
    }

    /** Converte incluindo a ficha técnica (para o detalhe do produto). */
    public static ProdutoDTO convert(Produto p, List<FichaTecnicaItemDTO> ficha) {
        if (p == null) {
            return null;
        }
        return new ProdutoDTO(
                p.getId(),
                p.getDescricao(),
                p.getPrecoVenda(),
                p.isAtivo(),
                ficha != null ? ficha : Collections.emptyList());
    }

    public static List<ProdutoDTO> convert(List<Produto> produtos) {
        if (produtos == null || produtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProdutoDTO> dtos = new ArrayList<>();
        for (Produto p : produtos) {
            if (p != null) {
                dtos.add(convert(p));
            }
        }
        return dtos;
    }
}
