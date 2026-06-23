package com.gestao.api.controllers.DTOs;

import java.util.List;

public record UserMeDTO(
        String nome,
        String email,
        String foto,
        String provider,
        Boolean googleVinculado,
        List<String> roles
) {}
