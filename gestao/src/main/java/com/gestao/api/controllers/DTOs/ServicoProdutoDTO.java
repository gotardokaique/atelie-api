package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gestao.api.entities.ServicoProduto;

/** Produto vinculado a um serviço (somente leitura). */
public record ServicoProdutoDTO(
        Long id,
        Long produtoId,
        String produtoDescricao,
        BigDecimal quantidade,
        BigDecimal precoVendaSnapshot,
        BigDecimal subtotal
) {

    public static ServicoProdutoDTO convert(ServicoProduto sp) {
        if (sp == null) {
            return null;
        }
        BigDecimal qtd = sp.getQuantidade() != null ? sp.getQuantidade() : BigDecimal.ZERO;
        BigDecimal preco = sp.getPrecoVendaSnapshot() != null ? sp.getPrecoVendaSnapshot() : BigDecimal.ZERO;

        return new ServicoProdutoDTO(
                sp.getId(),
                sp.getProduto() != null ? sp.getProduto().getId() : null,
                sp.getProduto() != null ? sp.getProduto().getDescricao() : null,
                qtd,
                preco,
                preco.multiply(qtd));
    }

    public static List<ServicoProdutoDTO> convert(List<ServicoProduto> itens) {
        if (itens == null || itens.isEmpty()) {
            return Collections.emptyList();
        }
        List<ServicoProdutoDTO> dtos = new ArrayList<>();
        for (ServicoProduto sp : itens) {
            if (sp != null) {
                dtos.add(convert(sp));
            }
        }
        return dtos;
    }
}
