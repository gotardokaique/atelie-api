package com.gestao.api.controllers.DTOs;

import jakarta.validation.constraints.NotBlank;

public record TipoServicoDTO(
    Long id,

    @NotBlank(message = "Nome do tipo de serviço não pode ser vazio")
    String nome
) {}