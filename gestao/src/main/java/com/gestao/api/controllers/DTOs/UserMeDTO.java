package com.gestao.api.controllers.DTOs;

public record UserMeDTO(
        String nome,
        String email,
        String foto,
        String provider,
        Boolean googleVinculado
) {}
