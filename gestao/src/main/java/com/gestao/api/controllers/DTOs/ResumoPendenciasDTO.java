package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

public record ResumoPendenciasDTO(
        BigDecimal valorTotalNaoPago,
        long quantidadePessoasComPendencia
) {}
