package com.gen.core.web.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1) // Executa antes de outros filtros (ex: SecurityFilter)
public class InputSanitizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InputSanitizationFilter.class);

    private static final Pattern STRONG_XSS_PATTERN = Pattern.compile(
            "(<script|</script|<iframe|</iframe|<img|<svg|onerror\\s*=|onload\\s*=|onclick\\s*=|onmouseover\\s*=|javascript:|data:text/html)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STRONG_SQL_PATTERN = Pattern.compile(
            "(--|/\\*|\\*/|;|\\bunion\\b|\\bdrop\\b|\\btruncate\\b|\\binsert\\b|\\bupdate\\b|\\bdelete\\b|\\bexec\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final int MAX_PARAM_LENGTH = 2000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
    	
    	if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
    	    filterChain.doFilter(request, response);
    	    return;
    	}

        // 1) Verifica se algum parâmetro/header/querystring é claramente malicioso
        if (hasMaliciousInput(request)) {
            log.warn("Requisição bloqueada por InputSanitizationFilter: payload suspeito detectado. URI={}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parâmetros inválidos.");
            return;
        }

        // 2) Envolve o request para aplicar uma limpeza leve (trim / remoção de controles)
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {

            @Override
            public String getParameter(String name) {
                return sanitize(super.getParameter(name));
            }

            @Override
            public String[] getParameterValues(String name) {
                String[] values = super.getParameterValues(name);
                if (values == null) return null;
                return Arrays.stream(values)
                        .map(InputSanitizationFilter::sanitize)
                        .toArray(String[]::new);
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                Map<String, String[]> original = super.getParameterMap();
                original.replaceAll((k, v) ->
                        v == null ? null :
                        Arrays.stream(v)
                              .map(InputSanitizationFilter::sanitize)
                              .toArray(String[]::new)
                );
                return original;
            }

            @Override
            public String getHeader(String name) {
                return sanitize(super.getHeader(name));
            }
        };

        // 3) Continua o fluxo normal
        filterChain.doFilter(wrapped, response);
    }

    /**
     * Verifica se algum parâmetro, header ou query string contém padrões fortes de ataque.
     */
    private boolean hasMaliciousInput(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (values == null) continue;

            for (String v : values) {
                if (isSuspicious(v)) {
                    log.warn("Parâmetro suspeito detectado: {}={}", key, v);
                    return true;
                }
            }
        }

        // Headers
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                Enumeration<String> headerValues = request.getHeaders(name);
                while (headerValues.hasMoreElements()) {
                    String v = headerValues.nextElement();
                    if (isSuspicious(v)) {
                        log.warn("Header suspeito detectado: {}={}", name, v);
                        return true;
                    }
                }
            }
        }

        // Query string bruta (caso algo não esteja em getParameterMap por algum motivo)
        String query = request.getQueryString();
        if (isSuspicious(query)) {
            log.warn("Query string suspeita detectada: {}", query);
            return true;
        }

        return false;
    }

    private boolean isSuspicious(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // WHITELIST HEADERS COMUNS
        String v = value.toLowerCase();
        if (v.equals("*/*") || v.contains("application/json") || v.contains("text/plain")) {
            return false;
        }

        if (value.length() > MAX_PARAM_LENGTH) {
            return true;
        }

        if (STRONG_XSS_PATTERN.matcher(value).find()) {
            return true;
        }

        if (STRONG_SQL_PATTERN.matcher(value).find()) {
            return true;
        }

        return false;
    }


    /**
     * Limpeza leve: remove caracteres de controle e faz trim.
     * Não tenta "corrigir" payload malicioso, apenas deixar input normal menos ruidoso.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        cleaned = cleaned.replace("\0", "");
        return cleaned.trim();
    }
}
