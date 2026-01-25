package com.gestao.api.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gestao.api.db.Condicao;
import com.gestao.api.db.DAOController;
import com.gestao.api.entities.Usuario;
import com.gestao.api.security.controller.SessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_TOKEN_LENGTH = 2048;

    private final JwtTokenProvider tokenProvider;
    private final DAOController daoController;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   DAOController daoController,
                                   SessionService sessionService) {
        this.tokenProvider = tokenProvider;
        this.daoController = daoController;
        this.sessionService = sessionService;
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

            // 3) Extrai token do header Authorization
            String jwt = getJwtFromRequest(request);
            if (!StringUtils.hasText(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (jwt.length() > MAX_TOKEN_LENGTH) {
                logger.warn("JWT com tamanho inválido ({} chars)", jwt.length());
                filterChain.doFilter(request, response);
                return;
            }

            // 4) Valida assinatura / expiração
            if (!tokenProvider.validateToken(jwt)) {
                logger.debug("JWT inválido ou expirado");
                filterChain.doFilter(request, response);
                return;
            }

            String username = tokenProvider.getUsernameFromJWT(jwt);
            if (!StringUtils.hasText(username)) {
                logger.debug("JWT sem subject válido");
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
                filterChain.doFilter(request, response);
                return;
            }

            if (!jwt.equals(tokenSalvo)) {
                logger.warn("JWT não coincide com token em sessão para usuário id={}", usuario.getId());
                filterChain.doFilter(request, response);
                return;
            }

            // 7) SLIDING EXPIRATION: renova a sessão para mais 1h
            sessionService.refreshSession(usuario.getId());

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

    private String getJwtFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (!StringUtils.hasText(authHeader)) {
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
        return StringUtils.hasText(token) ? token : null;
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
            || path.equals("/api/v1/auth/refresh");
    }
}
