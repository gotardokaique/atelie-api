package com.gestao.api.controllers.DTOs;

import java.time.LocalDate;

public record FiltroRelatorioDTO(
        LocalDate dataInicio,
        LocalDate dataFim,
        String tipoRelatorio,
        String formato
) {
}
