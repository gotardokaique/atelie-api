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

import com.gen.core.constants.FWConstante;
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
    
    @Value("${security.session.idle-expiration-seconds}")
    private long sessionIdleSeconds;

    private final long jwtExpirationMs;
    private final String cookieDomain;
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
        this.rotationThresholdMs = idleExpirationSeconds * 1000L + (skipSunday ? 86400L * 1000L : 0L);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,  HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
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
            if (StringUtils.hasText(jwt) == false) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = tokenService.validateToken(jwt);
            
            if (StringUtils.hasText(username) == false) {
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
            
            boolean isAppSession = HttpUtils.isMobileClient(request);

            String tokenSession;
            
            if (isAppSession) {
            	tokenSession = sessionService.get(FWConstante.appSessionKey(usuario.getId()));
            } else {
            	tokenSession = sessionService.get(FWConstante.sessionKey(usuario.getId()));
            }
            
            if (tokenSession == null) {
                clearAuthCookie(response);
                filterChain.doFilter(request, response);
                return;
            }

            if (jwt.equals(tokenSession) == false) {
                logger.warn("JWT não coincide com token em sessão para usuário id={}", usuario.getId());
                clearAuthCookie(response);
                filterChain.doFilter(request, response);
                return;
            }

            // App não expira por inatividade: a sessão vale até o fim do JWT emitido no login.
            if (isAppSession == false) {
            	sessionService.expire(FWConstante.sessionKey(usuario.getId()), sessionIdleSeconds);
            }
            
            roles = Select.rolesDoUsuario(usuario.getId(), daoController);

            long remainingMs = tokenService.getRemainingMs(jwt);

            if (remainingMs > 0 && remainingMs < rotationThresholdMs) {
            	long novoJwtExpirationMs = jwtExpirationMs * (isAppSession ? 3 : 1);
            	String novoJwt = tokenService.generateToken(usuario, roles, novoJwtExpirationMs);

            	if (isAppSession) {
            		sessionService.put(FWConstante.appSessionKey(usuario.getId()), novoJwt, novoJwtExpirationMs / 1000);
            		// App usa Bearer (não cookie): devolve o token novo no header pra ele salvar.
            		response.setHeader(FWConstante.HEADER_AUTH_TOKEN, novoJwt);
            	} else {
            		sessionService.put(FWConstante.sessionKey(usuario.getId()), novoJwt, sessionIdleSeconds);
            		HttpUtils.addSecureCookie(response, FWConstante.AUTH_COOKIE_NAME, novoJwt, (int) (novoJwtExpirationMs / 1000), cookieDomain);
            	}

                logger.debug("Token rotacionado para usuário id={}", usuario.getId());
            }

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority(FWConstante.ROLE_PREFIX + r))
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
        HttpUtils.removeCookie(response, FWConstante.AUTH_COOKIE_NAME);
    }
}
