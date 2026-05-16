package com.gestao.api.security.redefinir.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetInput(
        @Email @NotBlank String email) {
}