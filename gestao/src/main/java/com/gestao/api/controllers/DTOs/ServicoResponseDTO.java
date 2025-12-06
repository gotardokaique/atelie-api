package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;

public record ServicoResponseDTO(
    Long id,
    PessoaDTO pessoa, 
    String descricao,
    LocalDate dataEntregaPrevista,
    LocalDate dataCadastro,
    LocalDate dataFinalizacao,
    Boolean urgente,
    BigDecimal valor,
    StatusServico statusServico,
    StatusPagamento statusPagamento
) {}