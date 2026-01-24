package com.gestao.api.security.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.gestao.api.controllers.DTOs.LoginResponseDTO;
import com.gestao.api.db.TransactionDB;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.RoleEnum;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RegisterUserBO {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ===================== LOGIN SECURITY CONFIG =====================

    private static final int MAX_TENTATIVAS_LOGIN = 5;
    private static final long BLOQUEIO_MINUTOS = 2;

    // TTL geral da chave de tentativa (evita lixo eterno no Redis)
    // Pode ser mais longo que o bloqueio (ex: 30 min)
    private static final long TENTATIVA_TTL_MINUTOS = 30;

    private static final String LOGIN_ATTEMPT_KEY_PREFIX = "login:attempt:";
    private static final java.util.regex.Pattern REGEX_EMAIL =
            java.util.regex.Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final UsuarioServiceValidacao usuarioServiceValidacao;
    private final TransactionDB trans;

    public RegisterUserBO(UsuarioServiceValidacao usuarioServiceValidacao, TransactionDB trans) {
        this.usuarioServiceValidacao = usuarioServiceValidacao;
        this.trans = trans;
    }

    // ===================== MODEL INTERNO =====================

    private static class TentativaLogin {
        int tentativas;
        LocalDateTime bloqueadoAte;
        LocalDateTime ultimaTentativa;

        TentativaLogin() {
            this.tentativas = 0;
            this.bloqueadoAte = null;
            this.ultimaTentativa = LocalDateTime.now();
        }
    }

    // ===================== HELPERS REDIS (TENTATIVAS) =====================

    private String gerarChaveTentativa(String email) {
        return LOGIN_ATTEMPT_KEY_PREFIX + email;
    }

    private TentativaLogin carregarTentativa(String email) {
        String key = gerarChaveTentativa(email);
        Map<Object, Object> map = redisTemplate.opsForHash().entries(key);

        TentativaLogin t = new TentativaLogin();

        if (!map.isEmpty()) {
            String tentStr = (String) map.get("tentativas");
            String bloqueadoStr = (String) map.get("bloqueadoAte");
            String ultimaStr = (String) map.get("ultimaTentativa");

            if (tentStr != null) {
                try {
                    t.tentativas = Integer.parseInt(tentStr);
                } catch (NumberFormatException ignored) {
                    t.tentativas = 0;
                }
            }

            if (bloqueadoStr != null && !bloqueadoStr.isBlank()) {
                try {
                    t.bloqueadoAte = LocalDateTime.parse(bloqueadoStr);
                } catch (Exception ignored) {
                    t.bloqueadoAte = null;
                }
            }

            if (ultimaStr != null && !ultimaStr.isBlank()) {
                try {
                    t.ultimaTentativa = LocalDateTime.parse(ultimaStr);
                } catch (Exception ignored) {
                    t.ultimaTentativa = LocalDateTime.now();
                }
            }
        }

        return t;
    }

    private void salvarTentativa(String email, TentativaLogin tentativa) {
        String key = gerarChaveTentativa(email);

        redisTemplate.opsForHash().put(key, "tentativas", String.valueOf(tentativa.tentativas));
        redisTemplate.opsForHash().put(key, "ultimaTentativa", tentativa.ultimaTentativa.toString());

        if (tentativa.bloqueadoAte != null) {
            redisTemplate.opsForHash().put(key, "bloqueadoAte", tentativa.bloqueadoAte.toString());
        } else {
            redisTemplate.opsForHash().delete(key, "bloqueadoAte");
        }

        // TTL razoável pra não acumular lixo
        redisTemplate.expire(key, TENTATIVA_TTL_MINUTOS, TimeUnit.MINUTES);
    }

    private void resetTentativa(String email) {
        String key = gerarChaveTentativa(email);
        redisTemplate.delete(key);
    }

    // ===================== REGRAS DE NEGÓCIO =====================

    public Boolean validarSenhaForte(String senha) {
        if (senha == null) return false;
        if (senha.length() < 8) return false;

        boolean temMaiuscula = senha.matches(".*[A-Z].*");
        boolean temMinuscula = senha.matches(".*[a-z].*");
        boolean temNumero    = senha.matches(".*[0-9].*");
        // boolean temEspecial  = senha.matches(".*[^a-zA-Z0-9].*");

        if (!temMaiuscula || !temMinuscula || !temNumero) {
            return false;
        }

        // Evita repetições tipo "aaa", "1111"
        if (senha.matches(".*(.)\\1{2,}.*")) {
            return false;
        }

        return true;
    }

    public Boolean isEmailJaRegistrado(String email) {
        return usuarioServiceValidacao.validarEmailJaCadastrado(email);
    }

    public Boolean cadastrarUsuario(String nome, String email, String hashed, RoleEnum role) {
        boolean isUserCadastrado;
        var usuario = new Usuario(nome, email, hashed, role);

        try {
            trans.insert(usuario);
            isUserCadastrado = true;
        } catch (Exception e) {
            isUserCadastrado = false;
        }

        return isUserCadastrado;
    }

    // ===================== LOGIN (AGORA COM TENTATIVA EM REDIS) =====================

    public ResponseEntity<?> processarLogin(String emailRaw, String senha, HttpServletRequest request) {
        String email = emailRaw.trim().toLowerCase();

        if (!REGEX_EMAIL.matcher(email).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Formato de e-mail inválido"));
        }

        String clientIp = extrairIpCliente(request);
        TentativaLogin tentativa = carregarTentativa(email);
        tentativa.ultimaTentativa = LocalDateTime.now();

        if (tentativa.bloqueadoAte != null && tentativa.bloqueadoAte.isAfter(LocalDateTime.now())) {
            logger.warn("Login bloqueado para {} até {} (IP: {})", email, tentativa.bloqueadoAte, clientIp);
            salvarTentativa(email, tentativa); // atualiza ultimaTentativa
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Parece que você esqueceu sua senha... Tome um café e refresque a mente."));
        }

        try {
            var authToken = new UsernamePasswordAuthenticationToken(email, senha);
            Authentication auth = authenticationManager.authenticate(authToken);
            Usuario user = (Usuario) auth.getPrincipal();

            // Reset tentativas ao logar com sucesso
            resetTentativa(email);

            var jwt = tokenService.generateToken(user);
            sessionService.storeToken(user.getId(), jwt);

            logger.info("Login bem-sucedido para {} (IP: {})", email, clientIp);
            return ResponseEntity.ok(new LoginResponseDTO(jwt));

        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            tentativa.tentativas++;
            if (tentativa.tentativas >= MAX_TENTATIVAS_LOGIN) {
                tentativa.bloqueadoAte = LocalDateTime.now().plusMinutes(BLOQUEIO_MINUTOS);
                logger.warn("Conta bloqueada para {} até {} (IP: {})",
                        email, tentativa.bloqueadoAte, clientIp);
            }
            salvarTentativa(email, tentativa);

            logger.warn("Credenciais inválidas para {} (IP: {}): tentativa {} de {}. Motivo: {}",
                    email, clientIp, tentativa.tentativas, MAX_TENTATIVAS_LOGIN, e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Usuário ou senha inválidos"));

        } catch (Exception e) {
            logger.error("Erro inesperado ao autenticar {} (IP: {}): {}", email, clientIp, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro interno ao tentar autenticar. Tente novamente mais tarde."));
        }
    }

    // ===================== IP HELPER =====================

    private String extrairIpCliente(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            String[] parts = header.split(",");
            if (parts.length > 0) {
                return parts[0].trim();
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
