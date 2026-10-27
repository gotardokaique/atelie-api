package com.gestao.api.controllers.DTOs;

public class GoogleAuthRequest {

    // Validado no RegisterUserBO: ausente ou inválido responde 401 (não 400).
    private String idToken;

    public GoogleAuthRequest() {}

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
}
