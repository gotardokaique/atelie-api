package com.gestao.api.controllers.DTOs;
public record DashboardStatsDTO(
    long pendenteCount,
    long emAndamentoCount,
    long urgenteCount,
    long finalizadosSemanaCount
) {}