package com.gestao.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.gen.core.api.PublicEndpointRegistry;
import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gestao.api.entities.Usuario;
import com.gen.core.filter.BodySanitizingFilter;
import com.gen.core.filter.RequestLoggingFilter;
import com.gen.core.utils.HttpUtils;
import com.gestao.api.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DAOController daoController;
    private final PublicEndpointRegistry publicEndpointRegistry;
    private final RequestLoggingFilter requestLoggingFilter;
    private final BodySanitizingFilter bodySanitizingFilter;

    @Value("${app.security.cors.allowed-origins:https://genfinance.com.br}")
    private List<String> allowedOrigins;

    @Value("${app.security.require-https:true}")
    private boolean requireHttps;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          DAOController daoController,
                          PublicEndpointRegistry publicEndpointRegistry,
                          RequestLoggingFilter requestLoggingFilter,
                          BodySanitizingFilter bodySanitizingFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.daoController = daoController;
        this.publicEndpointRegistry = publicEndpointRegistry;
        this.requestLoggingFilter = requestLoggingFilter;
        this.bodySanitizingFilter = bodySanitizingFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // custo 12 é um bom equilíbrio entre segurança e performance
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationProvider authenticationProvider) throws Exception {

    	http
        .csrf(AbstractHttpConfigurer::disable)

        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .rememberMe(AbstractHttpConfigurer::disable);
    	
        if (requireHttps) {
            http.redirectToHttps(Customizer.withDefaults());
        }

        http
        .authorizeHttpRequests(auth -> auth
        		// Pré-flight CORS
        		.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        		// Endpoints marcados com @MethodMapping(isPublic = true) no gen-core
        		.requestMatchers(req -> publicEndpointRegistry.isPublic(req.getRequestURI())).permitAll()
        		.anyRequest().authenticated()
        		)

            .authenticationProvider(authenticationProvider)

            .addFilterBefore(requestLoggingFilter, SecurityContextHolderFilter.class)
            .addFilterBefore(bodySanitizingFilter, RequestLoggingFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Nao autorizado\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Acesso negado\"}");
                })
            )

            .headers(headers -> headers
            	    .addHeaderWriter((request, response) -> HttpUtils.addSecurityHeaders(response))
            	    .httpStrictTransportSecurity(hsts -> hsts
            	        .includeSubDomains(true)
            	        .maxAgeInSeconds(31536000)
            	    )
            	);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        final String DUMMY_HASH = "$2a$12$QeE1uWZKX8kI6gPlmYl5ye2oQ7wZJrGBwSJn1sRnTKe5BvWs3pQbC";

        return new AuthenticationProvider() {

            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String username = authentication.getName();
                String senhaRaw = authentication.getCredentials() != null
                        ? authentication.getCredentials().toString()
                        : "";

                if (username == null) {
                    throw new BadCredentialsException("Usuário ou senha inválidos.");
                }

                String email = username.trim().toLowerCase();

                if (email.length() > 120 || senhaRaw.length() > 120) {
                    throw new BadCredentialsException("Usuário ou senha inválidos.");
                }

                Usuario usuario;
                try {
                    usuario = daoController
                            .select()
                            .from(Usuario.class)
                            .where("email", Condicao.EQUAL, email)
                            .limit(1)
                            .one();
                } catch (Exception e) {
                	
                    passwordEncoder.matches(senhaRaw, DUMMY_HASH);
                    throw new BadCredentialsException("Usuário ou senha inválidos.");
                }

                if (!passwordEncoder.matches(senhaRaw, usuario.getSenha())) {
                    throw new BadCredentialsException("Usuário ou senha inválidos.");
                }

                if (!usuario.isAccountNonLocked() || !usuario.isEnabled()) {
                    throw new BadCredentialsException("Usuário ou senha inválidos.");
                }

                return new UsernamePasswordAuthenticationToken(
                        usuario,
                        null,
                        usuario.getAuthorities()
                );
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
//        config.setExposedHeaders(List.of("Set-Cookie"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "Origin", "Cache-Control"));

        config.setAllowCredentials(true);
        config.setMaxAge(7200L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(RequestLoggingFilter filter) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<BodySanitizingFilter> bodySanitizingFilterRegistration(BodySanitizingFilter filter) {
        FilterRegistrationBean<BodySanitizingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    
//    @PostConstruct
//    public void logCorsOrigins() {
//        System.out.println(">>> CORS allowedOrigins = " + allowedOrigins);
//        System.out.println(">>> requireHttps = " + requireHttps);
//    }
}
