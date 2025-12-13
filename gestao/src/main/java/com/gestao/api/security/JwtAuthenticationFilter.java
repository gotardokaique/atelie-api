package com.gestao.api.security;

import java.io.IOException;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.RoleEnum;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final DAOController daoController;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, DAOController daoController) {
        this.tokenProvider = tokenProvider;
        this.daoController = daoController;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) {
            logger.debug("JWT FILTER: Bypass para path de autenticação: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromJWT(jwt); // normalmente email

                UserDetails userDetails = carregarUserDetails(username);
                if (userDetails != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    logger.warn("JWT válido mas usuário não encontrado: {}", username);
                }
            }
        } catch (Exception ex) {
            logger.error("Não foi possível definir a autenticação do usuário no contexto de segurança", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }

    /**
     * Carrega o Usuario direto via DAOController e converte para UserDetails.
     */
    private UserDetails carregarUserDetails(String username) {
        try {
            Usuario usuario = daoController
                    .select()
                    .from(Usuario.class)
                    .where("email", Condicao.EQUAL, username.toLowerCase().trim())
                    .limit(1)
                    .one();

            // Se por algum motivo não tiver role, garante uma default
            RoleEnum role =  RoleEnum.ROLE_USER;

            return User
                    .withUsername(usuario.getEmail())
                    .password(usuario.getSenha()) // não é usado no fluxo JWT, mas o contrato pede
                    .authorities(role.name())
                    .accountExpired(false)
                    .accountLocked(false)
                    .credentialsExpired(false)
                    .disabled(false)
                    .build();

        } catch (Exception e) {
            logger.warn("Erro ao carregar usuário '{}' para autenticação JWT: {}", username, e.getMessage());
            return null;
        }
    }
}
