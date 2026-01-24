package com.gestao.api.controllers.DTOs;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;

public record ServicoResponseDTO(
        Long id,
        String descricao,
        BigDecimal valor,
        Boolean urgente,
        StatusServico statusServico,
        StatusPagamento statusPagamento,
        LocalDate dataEntregaPrevista,
        LocalDate dataFinalizacao,
        LocalDateTime dataCadastro,
        Long pessoaId,
        String pessoaNome
) implements Serializable {

    public static ServicoResponseDTO refactor(Servico servico) {
        return new ServicoResponseDTO(
                servico.getId(),
                servico.getDescricao(),
                servico.getValor(),
                servico.isUrgente(),
                servico.getStatusServico(),
                servico.getStatusPagamento(),
                servico.getDataEntregaPrevista(),
                servico.getDataFinalizacao(),
                servico.getDataCadastro(),
                servico.getPessoa() != null ? servico.getPessoa().getId() : null,
                servico.getPessoa() != null ? servico.getPessoa().getNome() : ""
                
        );
    }

    public static List<ServicoResponseDTO> refactor(List<Servico> servicos) {
        return servicos.stream()
                .filter(Objects::nonNull)
                .map(ServicoResponseDTO::refactor)
                .collect(Collectors.toList());
    }
}
