package com.gestao.api.controllers.DTOs;

/** Resultado de um disparo de push (teste ou job). */
public record EnvioPushDTO(int dispositivos, int enviados, int falhas, String erro) {

    public static EnvioPushDTO vazio() {
        return new EnvioPushDTO(0, 0, 0, null);
    }

    public EnvioPushDTO somar(EnvioPushDTO outro) {
        return new EnvioPushDTO(
                dispositivos + outro.dispositivos,
                enviados + outro.enviados,
                falhas + outro.falhas,
                erro != null ? erro : outro.erro);
    }
}
