package com.gestao.api.security.controller;

import org.springframework.stereotype.Service;

import com.gestao.api.entities.Usuario;
import com.gestao.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class TokenService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_TOKEN_LENGTH = 2048;

    private final JwtTokenProvider tokenProvider;

    public TokenService(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public String generateToken(Usuario user) {
        if (user == null || user.getEmail() == null) {
            throw new IllegalArgumentException("Usuário inválido para geração de token");
        }
        return tokenProvider.generateTokenFromUsername(user.getEmail());
    }

    public String validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        if (token.length() > MAX_TOKEN_LENGTH) {
            return null;
        }

        if (!tokenProvider.validateToken(token)) {
            return null;
        }

        return tokenProvider.getUsernameFromJWT(token);
    }

    public String getTokenFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            return null;
        }

        authHeader = authHeader.trim();
        if (authHeader.length() < BEARER_PREFIX.length()) {
            return null;
        }

        if (!authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            return null;
        }

        return token;
    }
}
