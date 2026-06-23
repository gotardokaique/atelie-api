package com.gestao.api.admin.dto;

import java.time.LocalDateTime;

public record TenantDetalheDTO(
        Long id,
        String nome,
        String email,
        String provider,
        Boolean ativo,
        LocalDateTime dataCadastro,
        LocalDateTime dataAtualizacao,
        long totalServicos,
        long totalClientes
) {}
