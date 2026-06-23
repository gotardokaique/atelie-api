package com.gestao.api.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.exception.NotFoundException;
import com.gen.core.security.SessionService;
import com.gen.core.security.TokenService;
import com.gen.core.utils.HttpUtils;
import com.gestao.api.entities.Usuario;
import com.gestao.api.select.Select;

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

        List<String> roles = new ArrayList<>();

        String path = request.getRequestURI();

        if (isPublicAuthPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            String jwt = tokenService.getTokenFromRequest(request);
            if (!StringUtils.hasText(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = tokenService.validateToken(jwt);
            if (!StringUtils.hasText(username)) {
                clearAuthCookie(response);
                filterChain.doFilter(request, response);
                return;
            }

            Usuario usuario = carregarUsuario(username);
            if (usuario == null) {
                logger.warn("JWT válido, mas usuário não encontrado: {}", username);
                filterChain.doFilter(request, response);
                return;
            }

            String tokenSalvo = sessionService.getToken(usuario.getId());
            if (tokenSalvo == null) {
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

            sessionService.refreshSession(usuario.getId());

            roles = Select.rolesDoUsuario(usuario.getId(), daoController);

            long remainingMs = tokenService.getRemainingMs(jwt);

            if (remainingMs > 0 && remainingMs < rotationThresholdMs) {
                String novoJwt = tokenService.generateToken(usuario, roles);
                sessionService.storeToken(usuario.getId(), novoJwt);
                HttpUtils.addSecureCookie(response, "auth_token", novoJwt,
                        (int) (jwtExpirationMs / 1000), cookieDomain);
                logger.debug("Token rotacionado para usuário id={}", usuario.getId());
            }

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(usuario, null, authorities);

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ex) {
            logger.error("Erro ao processar autenticação JWT", ex);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
    private Usuario carregarUsuario(String username) {
    	Usuario usuario = null;
    	try {
    		usuario = daoController
    				.select()
    				.from(Usuario.class)
    				.where("email", Condicao.EQUAL, username.toLowerCase().trim())
    				.one();
    		
    		return usuario;
    		
    	} catch (NotFoundException not) {
    		return usuario;
    	} catch (Exception e) {
    		logger.warn("Erro ao carregar usuário '{}' para autenticação JWT: {}", username, e.getMessage());
    		return usuario;
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
