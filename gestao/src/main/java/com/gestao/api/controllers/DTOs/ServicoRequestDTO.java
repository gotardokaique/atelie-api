package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ServicoRequestDTO(
    Long pessoaId, 

    String descricao,

    LocalDate dataEntregaPrevista,
    
    LocalDate dataFinalizacao,
    
    Boolean urgente,
    
    @PositiveOrZero(message = "Valor deve ser zero ou positivo")
    BigDecimal valor

) {}