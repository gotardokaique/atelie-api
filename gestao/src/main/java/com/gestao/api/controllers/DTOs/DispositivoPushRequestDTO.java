package com.gestao.api.controllers.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DispositivoPushRequestDTO(
        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^(ExponentPushToken|ExpoPushToken)\\[.+\\]$", message = "Token de push inválido.")
        String token,

        @NotBlank
        @Pattern(regexp = "(?i)^(android|ios)$", message = "Plataforma deve ser android ou ios.")
        String plataforma
) {
}
