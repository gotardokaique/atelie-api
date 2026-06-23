package com.gestao.api.admin.dto;

import java.time.LocalDateTime;

public record TenantListItemDTO(
        Long id,
        String nome,
        String email,
        String provider,
        Boolean ativo,
        LocalDateTime dataCadastro,
        long totalServicos,
        long totalClientes
) {}
