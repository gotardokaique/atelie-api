package com.gestao.api.admin.dto;

public record MetricasDTO(
        long totalUsuarios,
        long usuariosAtivos,
        long usuariosInativos,
        long novosNoMes
) {}
