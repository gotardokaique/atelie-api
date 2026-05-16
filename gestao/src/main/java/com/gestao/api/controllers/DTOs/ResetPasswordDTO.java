package com.gestao.api.controllers.DTOs;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordDTO(
        @NotBlank(message = "Token é obrigatório")
        String token,

        @NotBlank(message = "Nova senha é obrigatória")
        String newPassword
) {}
