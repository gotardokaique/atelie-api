package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

public record MeiMesDTO(
        int mes,
        BigDecimal valor,
        BigDecimal acumulado
) {}
