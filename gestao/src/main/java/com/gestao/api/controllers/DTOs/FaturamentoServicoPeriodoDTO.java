package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

public record FaturamentoServicoPeriodoDTO(
        String periodo,        // "MM/yyyy" (mensal) ou "dd/MM" (por dias)
        BigDecimal valorTotal, // soma do valor dos servicos finalizados e pagos
        Long quantidade        // contagem dos servicos finalizados e pagos
) {}
