package com.gestao.api.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.security.TokenService;
import com.gen.core.security.SessionService;
import com.gen.core.utils.HttpUtils;
import com.gestao.api.entities.Usuario;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenService tokenService;
    private final DAOController daoController;
    private final SessionService sessionService;

    private final long jwtExpirationMs;
    private final String cookieDomain;
    /** Reemite o token quando a vida restante cai abaixo deste limiar (= janela de idle). */
    private final long rotationThresholdMs;

    public JwtAuthenticationFilter(TokenService tokenService,
                                   DAOController daoController,
                                   SessionService sessionService,
                                   @Value("${api.security.jwt.expiration-ms}") long jwtExpirationMs,
                                   @Value("${app.security.cookie.domain:}") String cookieDomain,
                                   @Value("${security.session.idle-expiration-seconds}") long idleExpirationSeconds,
                                   @Value("${security.session.skip-sunday:true}") boolean skipSunday) {
        this.tokenService = tokenService;
        this.daoController = daoController;
        this.sessionService = sessionService;
        this.jwtExpirationMs = jwtExpirationMs;
        this.cookieDomain = cookieDomain;
        this.rotationThresholdMs = idleExpirationSeconds * 1000L
                + (skipSunday ? 86400L * 1000L : 0L);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isPublicAuthPath(path)) {
            logger.debug("JWT FILTER: Bypass para path de autenticação pública: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 2) Se já tem auth setado, não sobrescreve
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            // 3) Extrai token do cookie Auth_Token
            String jwt = tokenService.getTokenFromRequest(request);
            if (!StringUtils.hasText(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 4) Valida assinatura / expiração e obtém username
            String username = tokenService.validateToken(jwt);
            if (!StringUtils.hasText(username)) {
                logger.debug("JWT inválido, expirado ou sem subject");
                clearAuthCookie(response);
                filterChain.doFilter(request, response);
                return;
            }

            // 5) Carrega usuário
            Usuario usuario = carregarUsuario(username);
            if (usuario == null) {
                logger.warn("JWT válido, mas usuário não encontrado: {}", username);
                filterChain.doFilter(request, response);
                return;
            }

            // 6) Confere sessão no Redis
            String tokenSalvo = sessionService.getToken(usuario.getId());
            if (tokenSalvo == null) {
                logger.debug("Sessão expirada para usuário id={}. JWT rejeitado.", usuario.getId());
                clearAuthCookie(response);
                filterChain.doFilter(request, response);
                return;
            }

            if (!jwt.equals(tokenSalvo)) {
                logger.warn("JWT não coincide com token em sessão para usuário id={}", usuario.getId());
                clearAuthCookie(response);
                filterChain.doFilter(request, response);
                return;
            }
            // 7) SLIDING EXPIRATION: renova a sessão no Redis para mais 12h (janela de idle)
            sessionService.refreshSession(usuario.getId());

            // 7.1) ROTAÇÃO DE TOKEN: se o JWT entrou nas últimas 12h de vida, reemite
            // um token novo (3 dias), atualiza a sessão e reseta o cookie. Como o
            // usuário ativo sempre faz request dentro da janela de idle, ele cai aqui
            // antes do JWT vencer e nunca esbarra no teto de expiração.
            long remainingMs = tokenService.getRemainingMs(jwt);
            if (remainingMs > 0 && remainingMs < rotationThresholdMs) {
                String novoJwt = tokenService.generateToken(usuario);
                sessionService.storeToken(usuario.getId(), novoJwt);
                HttpUtils.addSecureCookie(response, "auth_token", novoJwt,
                        (int) (jwtExpirationMs / 1000), cookieDomain);
                logger.debug("Token rotacionado para usuário id={}", usuario.getId());
            }

            // 8) Autentica no contexto de segurança
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            usuario,
                            null,
                            usuario.getAuthorities()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ex) {
            logger.error("Erro ao processar autenticação JWT", ex);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }



    private Usuario carregarUsuario(String username) {
        try {
            return daoController
                    .select()
                    .from(Usuario.class)
                    .where("email", Condicao.EQUAL, username.toLowerCase().trim())
                    .limit(1)
                    .one();
        } catch (Exception e) {
            logger.warn("Erro ao carregar usuário '{}' para autenticação JWT: {}", username, e.getMessage());
            return null;
        }
    }
    
    private boolean isPublicAuthPath(String path) {
        return path.equals("/api/v1/auth/login")
            || path.equals("/api/v1/auth/register")
            || path.equals("/api/v1/auth/refresh")
            || path.equals("/api/v1/auth/forgot-password")
            || path.equals("/api/v1/auth/google");
    }
    
    private void clearAuthCookie(HttpServletResponse response) {
        com.gen.core.utils.HttpUtils.removeCookie(response, "auth_token");
    }
}
