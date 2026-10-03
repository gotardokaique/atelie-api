package com.gestao.api.controllers.DTOs;

import java.time.Instant;
import java.time.LocalDate;

import com.gestao.api.entities.Notificacao;
import com.gestao.api.enuns.TipoNotificacao;

public record NotificacaoDTO(
        Long id,
        TipoNotificacao tipo,
        String titulo,
        String descricao,
        Long servicoId,
        LocalDate dataReferencia,
        int quantidade,
        boolean resumo,
        Instant criadaEm,
        Instant visualizadaEm,
        Instant lidaEm
) {

    public static NotificacaoDTO refactor(Notificacao n) {
        return new NotificacaoDTO(
                n.getId(),
                n.getTipo(),
                n.getTitulo(),
                n.getDescricao(),
                n.getServicoId(),
                n.getDataReferencia(),
                n.getQuantidade() == null ? 1 : n.getQuantidade(),
                n.isResumo(),
                n.getCriadaEm(),
                n.getVisualizadaEm(),
                n.getLidaEm()
        );
    }
}
