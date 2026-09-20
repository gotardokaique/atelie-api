package com.gestao.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gen.core.api.PublicEndpointRegistry;
import com.gen.core.contracts.RolesProvider;
import com.gen.core.contracts.UserAccountLoader;
import com.gen.core.security.AbstractJwtAuthenticationFilter;
import com.gen.core.security.SessionService;
import com.gen.core.security.TokenService;

@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

    public JwtAuthenticationFilter(TokenService tokenService,
                                   SessionService sessionService,
                                   UserAccountLoader userAccountLoader,
                                   RolesProvider rolesProvider,
                                   PublicEndpointRegistry publicEndpointRegistry,
                                   @Value("${api.security.jwt.expiration-ms}") long jwtExpirationMs,
                                   @Value("${app.security.cookie.domain:}") String cookieDomain,
                                   @Value("${security.session.idle-expiration-seconds}") long idleExpirationSeconds,
                                   @Value("${security.session.skip-sunday:true}") boolean skipSunday) {
        super(tokenService, sessionService, userAccountLoader, rolesProvider, publicEndpointRegistry,
                jwtExpirationMs, cookieDomain, idleExpirationSeconds, skipSunday);
    }
}
