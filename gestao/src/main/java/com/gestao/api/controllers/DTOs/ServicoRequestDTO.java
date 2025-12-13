package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ServicoRequestDTO(
        Long pessoaId,
        String descricao,
        LocalDate dataEntregaPrevista,
        BigDecimal valor,
        Boolean urgente
) {}
