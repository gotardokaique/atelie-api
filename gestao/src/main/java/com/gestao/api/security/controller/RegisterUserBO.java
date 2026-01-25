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

    // Prefixos distintos para separar EMAIL e IP+EMAIL
    private static final String LOGIN_ATTEMPT_EMAIL_PREFIX    = "login:attempt:email:";
    private static final String LOGIN_ATTEMPT_IP_EMAIL_PREFIX = "login:attempt:ip_email:";

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

    private String gerarChaveTentativaEmail(String email) {
        return LOGIN_ATTEMPT_EMAIL_PREFIX + email;
    }

    private String gerarChaveTentativaIpEmail(String email, String ip) {
        return LOGIN_ATTEMPT_IP_EMAIL_PREFIX + ip + ":" + email;
    }

    private TentativaLogin carregarTentativaPorChave(String key) {
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

    private void salvarTentativaPorChave(String key, TentativaLogin tentativa) {
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

    private void resetTentativaPorChave(String key) {
        redisTemplate.delete(key);
    }

    // Wrappers específicos para EMAIL

    private TentativaLogin carregarTentativaEmail(String email) {
        return carregarTentativaPorChave(gerarChaveTentativaEmail(email));
    }

    private void salvarTentativaEmail(String email, TentativaLogin tentativa) {
        salvarTentativaPorChave(gerarChaveTentativaEmail(email), tentativa);
    }

    private void resetTentativaEmail(String email) {
        resetTentativaPorChave(gerarChaveTentativaEmail(email));
    }

    // Wrappers específicos para IP + EMAIL

    private TentativaLogin carregarTentativaIpEmail(String email, String ip) {
        return carregarTentativaPorChave(gerarChaveTentativaIpEmail(email, ip));
    }

    private void salvarTentativaIpEmail(String email, String ip, TentativaLogin tentativa) {
        salvarTentativaPorChave(gerarChaveTentativaIpEmail(email, ip), tentativa);
    }

    private void resetTentativaIpEmail(String email, String ip) {
        resetTentativaPorChave(gerarChaveTentativaIpEmail(email, ip));
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

    // ===================== LOGIN (AGORA COM EMAIL + IP+EMAIL EM REDIS) =====================

    public ResponseEntity<?> processarLogin(String emailRaw, String senha, HttpServletRequest request) {
        String email = emailRaw.trim().toLowerCase();

        if (!REGEX_EMAIL.matcher(email).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Formato de e-mail inválido"));
        }

        String clientIp = extrairIpCliente(request);
        LocalDateTime agora = LocalDateTime.now();

        // 1) Carrega tentativas por email e por ip+email
        TentativaLogin tentativaEmail = carregarTentativaEmail(email);
        TentativaLogin tentativaIpEmail = carregarTentativaIpEmail(email, clientIp);

        tentativaEmail.ultimaTentativa = agora;
        tentativaIpEmail.ultimaTentativa = agora;

        // 2) Verifica se está bloqueado em QUALQUER dimensão
        boolean bloqueadoPorEmail = tentativaEmail.bloqueadoAte != null && tentativaEmail.bloqueadoAte.isAfter(agora);
        boolean bloqueadoPorIpEmail = tentativaIpEmail.bloqueadoAte != null && tentativaIpEmail.bloqueadoAte.isAfter(agora);

        if (bloqueadoPorEmail || bloqueadoPorIpEmail) {
            logger.warn("Login bloqueado para {} até Email[{}], IP+Email[{}] (IP: {})",
                    email,
                    tentativaEmail.bloqueadoAte,
                    tentativaIpEmail.bloqueadoAte,
                    clientIp);

            // Atualiza ultimaTentativa em ambos
            salvarTentativaEmail(email, tentativaEmail);
            salvarTentativaIpEmail(email, clientIp, tentativaIpEmail);

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Parece que você esqueceu sua senha... Tome um café e refresque a mente."));
        }

        try {
            // 3) Tenta autenticar
            var authToken = new UsernamePasswordAuthenticationToken(email, senha);
            Authentication auth = authenticationManager.authenticate(authToken);
            Usuario user = (Usuario) auth.getPrincipal();

            // Login OK -> reset tentativas em ambas dimensões
            resetTentativaEmail(email);
            resetTentativaIpEmail(email, clientIp);

            var jwt = tokenService.generateToken(user);
            sessionService.storeToken(user.getId(), jwt);

            logger.info("Login bem-sucedido para {} (IP: {})", email, clientIp);
            return ResponseEntity.ok(new LoginResponseDTO(jwt));

        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {

            // 4) Login falhou: incrementa as DUAS dimensões
            tentativaEmail.tentativas++;
            tentativaIpEmail.tentativas++;

            if (tentativaEmail.tentativas >= MAX_TENTATIVAS_LOGIN
                    || tentativaIpEmail.tentativas >= MAX_TENTATIVAS_LOGIN) {

                LocalDateTime bloqueio = agora.plusMinutes(BLOQUEIO_MINUTOS);
                tentativaEmail.bloqueadoAte = bloqueio;
                tentativaIpEmail.bloqueadoAte = bloqueio;

                logger.warn("Conta bloqueada para {} até {} (IP: {}, tentativasEmail={}, tentativasIpEmail={})",
                        email, bloqueio, clientIp,
                        tentativaEmail.tentativas, tentativaIpEmail.tentativas);
            }

            salvarTentativaEmail(email, tentativaEmail);
            salvarTentativaIpEmail(email, clientIp, tentativaIpEmail);

            logger.warn("Credenciais inválidas para {} (IP: {}): tentativasEmail={} tentativasIpEmail={} de {}. Motivo: {}",
                    email, clientIp,
                    tentativaEmail.tentativas, tentativaIpEmail.tentativas,
                    MAX_TENTATIVAS_LOGIN,
                    e.getMessage());

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
