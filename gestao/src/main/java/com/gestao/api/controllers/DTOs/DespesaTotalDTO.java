package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

public record DespesaTotalDTO(
        BigDecimal totalDespesas,
        long quantidadeDespesas
) {}
