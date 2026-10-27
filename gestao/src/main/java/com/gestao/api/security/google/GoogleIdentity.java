package com.gestao.api.security.google;

/**
 * Identidade extraída de um ID token do Google já validado (assinatura, iss, aud, exp e email_verified).
 */
public record GoogleIdentity(String sub, String email, String nome, String foto) {
}
