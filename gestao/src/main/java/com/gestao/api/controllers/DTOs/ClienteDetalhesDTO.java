package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.gestao.api.entities.Pessoa;

public record ClienteDetalhesDTO(
        Long id,
        String nome,
        String telefone,
        String medidas,
        LocalDate dataCadastro,
        int totalServicos,
        BigDecimal totalGasto,
        int servicosPendentes,
        int servicosConcluidos,
        LocalDate ultimoAtendimento,
        List<ServicoHistoricoDTO> historico
) {

    public static ClienteDetalhesDTO refactor(
            Pessoa pessoa,
            int totalServicos,
            BigDecimal totalGasto,
            int servicosPendentes,
            int servicosConcluidos,
            LocalDate ultimoAtendimento,
            List<ServicoHistoricoDTO> historico
    ) {
        return new ClienteDetalhesDTO(
                pessoa.getId(),
                pessoa.getNome(),
                pessoa.getTelefone(),
                pessoa.getMedidas(),
                pessoa.getDataCadastro(),
                totalServicos,
                Objects.requireNonNullElse(totalGasto, BigDecimal.ZERO),
                servicosPendentes,
                servicosConcluidos,
                ultimoAtendimento,
                historico
        );
    }
}
