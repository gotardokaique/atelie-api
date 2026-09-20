package com.gestao.api.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gen.core.api.ApiResponse;
import com.gen.core.api.PublicEndpointRegistry;
import com.gen.core.filter.BodySanitizingFilter;
import com.gen.core.filter.RequestLoggingFilter;
import com.gen.core.utils.HttpUtils;
import com.gestao.api.security.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PublicEndpointRegistry publicEndpointRegistry;
    private final RequestLoggingFilter requestLoggingFilter;
    private final BodySanitizingFilter bodySanitizingFilter;

    @Value("${app.security.cors.allowed-origins:https://genfinance.com.br}")
    private List<String> allowedOrigins;

    @Value("${app.security.require-https:true}")
    private boolean requireHttps;

    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          PublicEndpointRegistry publicEndpointRegistry,
                          RequestLoggingFilter requestLoggingFilter,
                          BodySanitizingFilter bodySanitizingFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.publicEndpointRegistry = publicEndpointRegistry;
        this.requestLoggingFilter = requestLoggingFilter;
        this.bodySanitizingFilter = bodySanitizingFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
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
        		.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        		.requestMatchers(req -> publicEndpointRegistry.isPublic(req.getRequestURI())).permitAll()
        		.requestMatchers("/api/v1/z_admin/**").hasRole("SUPER_ADMIN")
        		.anyRequest().authenticated()
        		)

            .authenticationProvider(authenticationProvider)

            .addFilterBefore(requestLoggingFilter, SecurityContextHolderFilter.class)
            .addFilterBefore(bodySanitizingFilter, RequestLoggingFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            .exceptionHandling(ex -> ex
            	    .authenticationEntryPoint((req, res, e) -> {
            	        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            	        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            	        res.setCharacterEncoding("UTF-8");
            	        objectMapper.writeValue(res.getWriter(), ApiResponse.error("Não autorizado."));
            	    })
            	    .accessDeniedHandler((req, res, e) -> {
            	        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            	        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            	        res.setCharacterEncoding("UTF-8");
            	        objectMapper.writeValue(res.getWriter(), ApiResponse.error("Acesso negado."));
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
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
//        config.setExposedHeaders(List.of("Set-Cookie"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "Origin", "Cache-Control", "Authorization", "X-Client-Type", "X-Client-Key"));

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
