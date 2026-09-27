package com.gestao.api.controllers.DTOs;

import com.gestao.api.enuns.TipoAnexo;

public record AnexoRequestDTO(
        TipoAnexo type,
        String name,
        String contentType,
        Long sizeBytes
) {}