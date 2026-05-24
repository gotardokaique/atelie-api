package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gestao.api.entities.MovimentacaoEstoque;
import com.gestao.api.enuns.TipoMovimentacao;

/** Linha do extrato de movimentações (somente leitura). */
public record MovimentacaoDTO(
        Long id,
        Long insumoId,
        String insumoDescricao,
        Long servicoId,
        TipoMovimentacao tipo,
        BigDecimal quantidade,
        BigDecimal custoUnitario,
        LocalDateTime dataMovimentacao,
        String observacao
) {

    public static MovimentacaoDTO convert(MovimentacaoEstoque m) {
        if (m == null) {
            return null;
        }
        return new MovimentacaoDTO(
                m.getId(),
                m.getInsumo() != null ? m.getInsumo().getId() : null,
                m.getInsumo() != null ? m.getInsumo().getDescricao() : null,
                m.getServico() != null ? m.getServico().getId() : null,
                m.getTipo(),
                m.getQuantidade(),
                m.getCustoUnitario(),
                m.getDataMovimentacao(),
                m.getObservacao());
    }

    public static List<MovimentacaoDTO> convert(List<MovimentacaoEstoque> movs) {
        if (movs == null || movs.isEmpty()) {
            return Collections.emptyList();
        }
        List<MovimentacaoDTO> dtos = new ArrayList<>();
        for (MovimentacaoEstoque m : movs) {
            if (m != null) {
                dtos.add(convert(m));
            }
        }
        return dtos;
    }
}
