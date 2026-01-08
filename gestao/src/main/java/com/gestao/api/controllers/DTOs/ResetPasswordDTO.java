package com.gestao.api.controllers.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordDTO(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Formato de email inválido")
        String email,

        @NotBlank(message = "Código é obrigatório")
        String code,

        @NotBlank(message = "Nova senha é obrigatória")
        String newPassword
) {}
