package com.gestao.api.controllers.DTOs;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.gestao.api.enuns.TipoNotificacao;

/** Dry-run do job de prazos para um usuário: nada é gravado nem enviado. */
public record SimulacaoNotificacaoDTO(
        Long usuarioId,
        String nome,
        String email,
        String fuso,
        LocalDate hoje,
        boolean notificarPrazo,
        int diasAntecedencia,
        long dispositivosAtivos,
        int novas,
        int jaExistentes,
        Map<TipoNotificacao, Integer> porTipo,
        List<String> titulos
) {
}
