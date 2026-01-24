package com.gestao.api.security;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${api.security.jwt.secret}")
    private String jwtSecret;

    @Value("${api.security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30L;

    private SecretKey getSigningKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(this.jwtSecret);

            if (keyBytes.length < 64) {
                logger.warn("JWT secret possui {} bytes após decode. Ideal para HS512 é >= 64 bytes. Fortaleça o secret em produção.", keyBytes.length);
            }

            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException ex) {
            // Isso indica configuração ERRADA no application.properties
            logger.error("JWT secret inválido (não é Base64 válido). Verifique api.security.jwt.secret", ex);
            throw new IllegalStateException("Configuração de JWT secret inválida", ex);
        }
    }

    public String generateToken(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String username;

        if (principal instanceof UserDetails userDetails) {
        	
            username = userDetails.getUsername();
        } else if (principal != null) {
        	
            username = principal.toString();
        } else {
        	
            throw new IllegalArgumentException("Principal nulo ao gerar token JWT");
        }

        return generateTokenFromUsername(username);
    }

    /**
     * Gera token a partir do username (email). Ponto único de geração.
     */
    public String generateTokenFromUsername(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setId(UUID.randomUUID().toString()) 
                .setSubject(username)                   // sub
                .setIssuedAt(now)                       // iat
                .setExpiration(expiryDate)              // exp
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Extrai o username (subject) do token.
     */
    public String getUsernameFromJWT(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    /**
     * Valida o token:
     * - assinatura
     * - integridade
     * - expiração
     */
    public boolean validateToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (SignatureException ex) {
            logger.debug("Assinatura JWT inválida");
        } catch (MalformedJwtException ex) {
            logger.debug("Token JWT malformado");
        } catch (ExpiredJwtException ex) {
            logger.debug("Token JWT expirado");
        } catch (UnsupportedJwtException ex) {
            logger.debug("Token JWT não suportado");
        } catch (IllegalArgumentException ex) {
            logger.debug("Token JWT vazio ou nulo");
        } catch (Exception ex) {
            // fallback para qualquer coisa inesperada
            logger.error("Erro inesperado ao validar token JWT", ex);
        }
        return false;
    }

    /**
     * Parser centralizado com clock skew configurado.
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .setAllowedClockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
