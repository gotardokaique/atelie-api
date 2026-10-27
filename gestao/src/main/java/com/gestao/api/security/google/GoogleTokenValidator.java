package com.gestao.api.security.google;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Valida o ID token do Google: assinatura (JWKS do Google), iss, aud, exp e email_verified.
 * Só devolve a identidade quando tudo confere; qualquer falha resulta em Optional vazio.
 */
@Component
public class GoogleTokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(GoogleTokenValidator.class);

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    private static final int TIMEOUT_MS = 5000;

    @Value("${APP_GOOGLE_CLIENT_IDS:}")
    private List<String> clientIds;

    private Set<String> audienciasAceitas = Set.of();
    private NimbusJwtDecoder decoder;

    @PostConstruct
    void iniciar() {
        audienciasAceitas = clientIds == null ? Set.of()
                : clientIds.stream().map(String::trim).filter(id -> id.isEmpty() == false).collect(java.util.stream.Collectors.toSet());

        if (audienciasAceitas.isEmpty()) {
            logger.error("[SECURITY] APP_GOOGLE_CLIENT_IDS não configurado: login com Google ficará indisponível.");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);

        decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(factory))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                this::validarClaims));
    }

    public Optional<GoogleIdentity> validar(String idToken) {
        if (idToken == null || idToken.isBlank() || audienciasAceitas.isEmpty()) {
            return Optional.empty();
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken.trim());
        } catch (BadJwtException e) {
            logger.warn("[SECURITY] ID token do Google rejeitado: {}", e.getMessage());
            return Optional.empty();
        } catch (JwtException e) {
            logger.error("[SECURITY] Falha ao validar ID token do Google (JWKS indisponível?): {}", e.getMessage());
            return Optional.empty();
        }

        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new GoogleIdentity(
                sub,
                email.trim().toLowerCase(),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture")));
    }

    private OAuth2TokenValidatorResult validarClaims(Jwt jwt) {
        String iss = jwt.getClaimAsString("iss");
        if (iss == null || ISSUERS.contains(iss) == false) {
            return falha("iss inválido");
        }

        List<String> aud = jwt.getAudience();
        if (aud == null || aud.stream().anyMatch(audienciasAceitas::contains) == false) {
            return falha("aud não aceito");
        }

        if (jwt.getExpiresAt() == null) {
            return falha("exp ausente");
        }

        if (emailVerificado(jwt.getClaim("email_verified")) == false) {
            return falha("email não verificado");
        }

        return OAuth2TokenValidatorResult.success();
    }

    // O Google emite email_verified como boolean; aceita "true" em string por segurança.
    private boolean emailVerificado(Object valor) {
        if (valor instanceof Boolean b) {
            return b;
        }
        return valor != null && "true".equalsIgnoreCase(valor.toString());
    }

    private OAuth2TokenValidatorResult falha(String descricao) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", descricao, null));
    }
}
