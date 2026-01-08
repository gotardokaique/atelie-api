package com.gestao.api.controllers.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordDTO(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Formato de email inválido")
        String email
) {}
