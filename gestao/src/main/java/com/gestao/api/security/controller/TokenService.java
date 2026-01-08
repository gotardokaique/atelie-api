package com.gestao.api.security.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gestao.api.entities.Usuario;
import com.gestao.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class TokenService {

    @Autowired
    private JwtTokenProvider tokenProvider;

    public String generateToken(Usuario user) {
        return tokenProvider.generateTokenFromUsername(user.getEmail());
    }

    public String validateToken(String token) {
        if (tokenProvider.validateToken(token)) {
            return tokenProvider.getUsernameFromJWT(token);
        }
        return null;
    }

    private Instant genExpirationDate() {
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }

    public String getTokenFromRequest(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null)
            return null;
        return authHeader.replace("Bearer ", "");
    }
}
