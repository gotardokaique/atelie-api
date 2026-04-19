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

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gestao.api.entities.Usuario;
import com.gestao.api.security.JwtAuthenticationFilter;

import jakarta.annotation.PostConstruct;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DAOController daoController;

    @Value("${app.security.cors.allowed-origins:https://genfinance.com.br}")
    private List<String> allowedOrigins;

    @Value("${app.security.require-https:true}")
    private boolean requireHttps;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          DAOController daoController) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.daoController = daoController;
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
        // JWT + stateless => CSRF desabilitado
        .csrf(AbstractHttpConfigurer::disable)

        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .rememberMe(AbstractHttpConfigurer::disable);
    	
        // HTTPS obrigatório (em produção) usando API nova
        if (requireHttps) {
            http.redirectToHttps(Customizer.withDefaults());
        }

        http
        .authorizeHttpRequests(auth -> auth
        		// Pré-flight CORS
        		.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        		.requestMatchers("/api/v1/auth/login", "/api/v1/auth/register").permitAll()
        		.anyRequest().authenticated()
        		)

            .authenticationProvider(authenticationProvider)

            // Filtro de JWT antes do UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Não autorizado\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Acesso negado\"}");
                })
            )

            .headers(headers -> headers
                // Impede iframes (clickjacking)
                .frameOptions(frame -> frame.deny())
                // Remove sniffing de tipo de conteúdo
                .contentTypeOptions(Customizer.withDefaults())
                // HSTS se HTTPS estiver exigido (browsers só falarão HTTPS com esse host)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000) // 1 ano
                )
            );

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {

        // hash dummy para timing mitigations quando usuário não existe
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

                // Pequena barreira contra inputs absurdos
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
                    // Usuário não existe: queima tempo com um matches no dummy
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

        // carrega de propriedade: app.security.cors.allowed-origins
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        // evita preflight toda hora (opcional)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    
    @PostConstruct
    public void logCorsOrigins() {
        System.out.println(">>> CORS allowedOrigins = " + allowedOrigins);
        System.out.println(">>> requireHttps = " + requireHttps);
    }
}
