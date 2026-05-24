package com.gestao.api.security.redefinir.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordUpdateWithTokenInput(
        @NotBlank String password,
        @NotBlank String token) {
}