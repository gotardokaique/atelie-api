package com.gestao.api.controllers.DTOs;

public record RegistroUsuarioRequestDTO(
        String nome,
        String email,
        String senha
) {}
