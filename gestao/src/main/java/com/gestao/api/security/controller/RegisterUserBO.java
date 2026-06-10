package com.gestao.api.security.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.gen.core.db.Condicao;
import com.gen.core.db.QueryBuilder;

import com.gen.core.db.TransactionDB;
import com.gen.core.db.exception.NotFoundException;
import com.gen.core.security.SessionService;
import com.gen.core.security.TokenService;
import com.gen.core.utils.HttpUtils;
import com.gen.core.utils.StringEncryptUtils;
import com.gestao.api.bo.EmailBO;
import com.gestao.api.controllers.DTOs.GoogleAuthRequest;
import com.gestao.api.controllers.DTOs.LoginResponseDTO;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.ProviderUsuario;
import com.gestao.api.enuns.RoleEnum;
import com.gestao.api.security.redefinir.RedefinirSenhaService;
import com.gestao.api.select.Select;
import com.gestao.api.services.exceptions.BusinessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

    @Autowired
    private RedefinirSenhaService redefinirSenhaService;

    @Autowired
    private StringEncryptUtils stringEncryptUtils;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${api.security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.security.cookie.domain:}")
    private String cookieDomain;

    @Value("${app.admin.email:kaiquecgotardo@gmail.com}")
    private String adminEmail;

    // ===================== LOGIN SECURITY CONFIG =====================

    private static final int MAX_TENTATIVAS_LOGIN = 5;
    private static final long BLOQUEIO_MINUTOS = 2;
    private final EmailBO emailBO;

    // TTL geral da chave de tentativa (evita lixo eterno no Redis)
    // Pode ser mais longo que o bloqueio (ex: 30 min)
    private static final long TENTATIVA_TTL_MINUTOS = 30;

    // Prefixos distintos para separar EMAIL e IP+EMAIL
    private static final String LOGIN_ATTEMPT_EMAIL_PREFIX = "login:attempt:email:";
    private static final String LOGIN_ATTEMPT_IP_EMAIL_PREFIX = "login:attempt:ip_email:";

    private static final java.util.regex.Pattern REGEX_EMAIL = java.util.regex.Pattern
            .compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final UsuarioServiceValidacao usuarioServiceValidacao;
    private final TransactionDB trans;

    public RegisterUserBO(UsuarioServiceValidacao usuarioServiceValidacao, TransactionDB trans,
            RedefinirSenhaService redefinirSenhaService, EmailBO emailBO) {
        this.usuarioServiceValidacao = usuarioServiceValidacao;
        this.trans = trans;
        this.redefinirSenhaService = redefinirSenhaService;
        this.emailBO = emailBO;
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
        if (senha == null)
            return false;
        if (senha.length() < 8)
            return false;

        boolean temMaiuscula = senha.matches(".*[A-Z].*");
        boolean temMinuscula = senha.matches(".*[a-z].*");
        boolean temNumero = senha.matches(".*[0-9].*");
        // boolean temEspecial = senha.matches(".*[^a-zA-Z0-9].*");

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
        try {
            return usuarioServiceValidacao.validarEmailJaCadastrado(email);
        } catch (Exception e) {
            return true;
        }
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

        if (isUserCadastrado) {
            notificarAdminNovoUsuario(nome, email, ProviderUsuario.LOCAL);
        }

        return isUserCadastrado;
    }

    // ===================== LOGIN (AGORA COM EMAIL + IP+EMAIL EM REDIS)
    // =====================

    public ResponseEntity<?> processarLogin(String emailRaw, String senha, HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        String email = emailRaw.trim().toLowerCase();

        if (!REGEX_EMAIL.matcher(email).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Formato de e-mail inválido"));
        }

        try {
            Usuario usuarioCheck = new QueryBuilder(trans).select()
                    .from(Usuario.class)
                    .where("email", Condicao.EQUAL, email)
                    .one();
            if (usuarioCheck.getProvider() == ProviderUsuario.GOOGLE) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Esta conta usa login com Google. Entre pelo botão do Google."));
            }
        } catch (Exception ignorado) {
            // usuário não encontrado ou erro — fluxo normal trata adiante
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
        boolean bloqueadoPorIpEmail = tentativaIpEmail.bloqueadoAte != null
                && tentativaIpEmail.bloqueadoAte.isAfter(agora);

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

            HttpUtils.addSecureCookie(response, "auth_token", jwt, (int) (jwtExpirationMs / 1000), cookieDomain);

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

            logger.warn(
                    "Credenciais inválidas para {} (IP: {}): tentativasEmail={} tentativasIpEmail={} de {}. Motivo: {}",
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

    public void processarRedefinirSenha(String email) {
        try {
            Usuario usuario = Select.buscarUsuarioPorEmail(trans, email);
            String token = redefinirSenhaService.generateToken(usuario);
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
            String resetLink = frontendUrl + "/reset-password?token=" + encodedToken;

            emailBO.criar().remetente().destinatario(email)
                    .mensagem(buildPasswordResetEmail(resetLink, usuario.getNome()))
                    .titulo("Redefinição de Senha — La Femme Ateliê")
                    .enviar();

        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar token");
        }
    }

    public void resetarSenha(String token, String novaSenha) {
        try {
            redefinirSenhaService.changePassword(novaSenha, token);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Erro ao redefinir senha");
        }
    }

    private static String buildPasswordResetEmail(String resetLink, String nome) {
        String saudacao = (nome != null && !nome.isBlank()) ? ", " + nome : "";
        String ano = String.valueOf(java.time.Year.now().getValue());
        return
            "<!DOCTYPE html>" +
            "<html lang='pt-BR'><head>" +
            "<meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
            "<title>Redefini&ccedil;&atilde;o de Senha</title></head>" +
            "<body style='margin:0;padding:0;background-color:#f0ece6;" +
            "font-family:Helvetica Neue,Arial,sans-serif;'>" +

            "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'" +
            " style='background-color:#f0ece6;padding:48px 16px;'>" +
            "<tr><td align='center'>" +

            // ── CARD ─────────────────────────────────────────────────────────
            "<table role='presentation' cellpadding='0' cellspacing='0' border='0'" +
            " style='width:100%;max-width:560px;background-color:#ffffff;" +
            "border-radius:8px;overflow:hidden;box-shadow:0 4px 32px rgba(0,0,0,0.10);'>" +

            // HEADER
            "<tr><td style='background-color:#111111;padding:38px 48px 34px;text-align:center;'>" +
            "<p style='margin:0 0 8px 0;font-size:10px;letter-spacing:5px;color:#b8956a;" +
            "text-transform:uppercase;font-weight:600;'>Gest&atilde;o de Atel&icirc;</p>" +
            "<h1 style='margin:0;color:#ffffff;font-size:28px;font-weight:300;" +
            "letter-spacing:4px;text-transform:uppercase;'>La Femme</h1>" +
            "<div style='width:36px;height:2px;background-color:#b8956a;margin:14px auto 0;'></div>" +
            "</td></tr>" +

            // Gold gradient bar
            "<tr><td style='height:3px;" +
            "background:linear-gradient(90deg,#6b4e10,#c9a96e,#e8d5a3,#c9a96e,#6b4e10);" +
            "font-size:0;line-height:3px;'>&nbsp;</td></tr>" +

            // BODY
            "<tr><td style='padding:52px 48px 44px;text-align:center;'>" +

            "<h2 style='margin:0 0 22px;color:#111111;font-size:22px;" +
            "font-weight:600;letter-spacing:-0.3px;'>Redefini&ccedil;&atilde;o de Senha</h2>" +

            "<p style='margin:0 0 6px;color:#333333;font-size:15px;line-height:1.7;'>" +
            "Ol&aacute;" + saudacao + ",</p>" +
            "<p style='margin:0 0 36px;color:#666666;font-size:15px;line-height:1.8;'>" +
            "Recebemos uma solicita&ccedil;&atilde;o de redefini&ccedil;&atilde;o de senha " +
            "para sua conta.<br>Clique no bot&atilde;o abaixo para criar uma nova senha " +
            "com seguran&ccedil;a.</p>" +

            // CTA button
            "<a href='" + resetLink + "'" +
            " style='display:inline-block;background-color:#111111;color:#c9a96e;" +
            "text-decoration:none;padding:16px 52px;border-radius:2px;" +
            "font-size:12px;font-weight:700;letter-spacing:3px;text-transform:uppercase;" +
            "border:1.5px solid #c9a96e;'>Redefinir Minha Senha</a>" +

            // Divider
            "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'" +
            " style='margin-top:40px;margin-bottom:28px;'><tr>" +
            "<td style='height:1px;background-color:#ede8e0;font-size:0;line-height:1px;'>" +
            "&nbsp;</td></tr></table>" +

            // Notice — expiry
            "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'>" +
            "<tr><td style='background-color:#faf7f3;border-radius:6px;padding:18px 22px;" +
            "border-left:3px solid #c9a96e;text-align:left;'>" +
            "<p style='margin:0 0 5px;color:#111111;font-size:13px;font-weight:700;'>" +
            "&#8987;&nbsp; Validade do link</p>" +
            "<p style='margin:0;color:#777777;font-size:13px;line-height:1.6;'>" +
            "Este link expira em <strong style='color:#111111;'>5 minutos</strong>. " +
            "Solicite um novo caso ele expire.</p>" +
            "</td></tr>" +
            "<tr><td style='height:12px;'></td></tr>" +

            // Notice — not you
            "<tr><td style='background-color:#faf7f3;border-radius:6px;padding:18px 22px;" +
            "border-left:3px solid #b8956a;text-align:left;'>" +
            "<p style='margin:0 0 5px;color:#111111;font-size:13px;font-weight:700;'>" +
            "&#128274;&nbsp; N&atilde;o reconhece esta solicita&ccedil;&atilde;o?</p>" +
            "<p style='margin:0;color:#777777;font-size:13px;line-height:1.6;'>" +
            "Se voc&ecirc; n&atilde;o solicitou a redefini&ccedil;&atilde;o, ignore este " +
            "e-mail. Sua senha permanece inalterada.</p>" +
            "</td></tr>" +
            "</table>" +

            "</td></tr>" +

            // FOOTER
            "<tr><td style='background-color:#111111;padding:28px 48px;text-align:center;'>" +
            "<p style='margin:0 0 8px;color:#b8956a;font-size:10px;letter-spacing:3px;" +
            "text-transform:uppercase;font-weight:500;'>La Femme Atel&icirc;</p>" +
            "<p style='margin:0;color:#555555;font-size:11px;line-height:1.7;'>" +
            "&copy; " + ano + " Todos os direitos reservados &nbsp;&middot;&nbsp; " +
            "N&atilde;o responda este e-mail</p>" +
            "</td></tr>" +

            "</table>" +

            "<p style='margin:22px 0 0;color:#aaaaaa;font-size:11px;text-align:center;'>" +
            "Voc&ecirc; recebeu este e-mail porque solicitou a redefini&ccedil;&atilde;o " +
            "de senha da sua conta.</p>" +

            "</td></tr></table>" +
            "</body></html>";
    }

    // ===================== AUTENTICAÇÃO GOOGLE =====================

    public ResponseEntity<?> autenticarComGoogle(GoogleAuthRequest request, HttpServletResponse response)
            throws Exception {
        String email = request.getEmail().trim().toLowerCase();

        // 1. Buscar por googleId
        Usuario usuario = null;
        try {
            usuario = new QueryBuilder(trans).select()
                    .from(Usuario.class)
                    .where("googleId", Condicao.EQUAL, request.getGoogleId())
                    .one();
        } catch (Exception ignorado) {
            // não encontrado por googleId
        }

        if (usuario == null) {
            // 2. Buscar por email
            Usuario porEmail = null;
            try {
                porEmail = new QueryBuilder(trans).select()
                        .from(Usuario.class)
                        .where("email", Condicao.EQUAL, email)
                        .one();
            } catch (Exception ignorado) {
                // não encontrado por email
            }

            if (porEmail != null) {
                // 3. Conta LOCAL com mesmo email — rejeitar
                throw new BusinessException("Este email já está cadastrado com senha. Faça login com email e senha.");
            }

            // 4. Criar novo usuário Google
            var novoUsuario = new Usuario();
            novoUsuario.setEmail(email);
            novoUsuario.setNome(request.getNome());
            novoUsuario.setGoogleId(request.getGoogleId());
            novoUsuario.setProvider(ProviderUsuario.GOOGLE);
            novoUsuario.setSenha(stringEncryptUtils.encrypt(request.getGoogleId()));
            novoUsuario.setAtivo(true);
            trans.insert(novoUsuario);

            // Re-fetch para garantir ID gerado pelo banco
            usuario = new QueryBuilder(trans).select()
                    .from(Usuario.class)
                    .where("googleId", Condicao.EQUAL, request.getGoogleId())
                    .one();

            // Notifica o admin sobre o novo cadastro via Google.
            notificarAdminNovoUsuario(request.getNome(), email, ProviderUsuario.GOOGLE);
        }

        // 5. Gerar JWT — mesmo fluxo do login normal
        var jwt = tokenService.generateToken(usuario);
        sessionService.storeToken(usuario.getId(), jwt);
        HttpUtils.addSecureCookie(response, "auth_token", jwt, (int) (jwtExpirationMs / 1000));

        return ResponseEntity.ok(new LoginResponseDTO(jwt));
    }

    // ===================== NOTIFICAÇÃO ADMIN =====================

    /**
     * Envia um e-mail ao admin avisando que um novo usuário se cadastrou
     * (manual ou via Google). Não propaga exceções: falhar o envio NÃO deve
     * derrubar o cadastro/login do usuário.
     */
    private void notificarAdminNovoUsuario(String nome, String email, ProviderUsuario provider) {
        try {
            if (adminEmail == null || adminEmail.isBlank()) {
                logger.warn("Notificação de novo usuário ignorada: admin email não configurado.");
                return;
            }

            String providerLabel = provider == ProviderUsuario.GOOGLE ? "Google" : "Email/Senha";
            String titulo = "Novo cadastro no Gestão Ateliê — " + (nome == null ? email : nome);
            String corpo = buildNovoUsuarioEmail(nome, email, providerLabel);

            emailBO.criar()
                    .remetente()
                    .destinatario(adminEmail)
                    .titulo(titulo)
                    .mensagem(corpo)
                    .enviar();

            logger.info("Notificação de novo usuário enviada ao admin ({}) — provider={}", adminEmail, providerLabel);
        } catch (Exception e) {
            // Email é fire-and-forget aqui; logar e seguir.
            logger.warn("Falha ao notificar admin sobre novo usuário {} ({}): {}", email, provider, e.getMessage());
        }
    }

    private static String buildNovoUsuarioEmail(String nome, String email, String providerLabel) {
        String nomeSeguro = (nome == null || nome.isBlank()) ? "(sem nome)" : nome;
        String quando = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String ano = String.valueOf(java.time.Year.now().getValue());
        return
            "<!DOCTYPE html>" +
            "<html lang='pt-BR'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
            "<title>Novo cadastro</title></head>" +
            "<body style='margin:0;padding:0;background-color:#f0ece6;font-family:Helvetica Neue,Arial,sans-serif;'>" +

            "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'" +
            " style='background-color:#f0ece6;padding:48px 16px;'>" +
            "<tr><td align='center'>" +

            "<table role='presentation' cellpadding='0' cellspacing='0' border='0'" +
            " style='width:100%;max-width:560px;background-color:#ffffff;" +
            "border-radius:8px;overflow:hidden;box-shadow:0 4px 32px rgba(0,0,0,0.10);'>" +

            // HEADER
            "<tr><td style='background-color:#111111;padding:38px 48px 34px;text-align:center;'>" +
            "<p style='margin:0 0 8px 0;font-size:10px;letter-spacing:5px;color:#b8956a;" +
            "text-transform:uppercase;font-weight:600;'>Gest&atilde;o de Atel&icirc;</p>" +
            "<h1 style='margin:0;color:#ffffff;font-size:24px;font-weight:300;" +
            "letter-spacing:3px;text-transform:uppercase;'>Novo cadastro</h1>" +
            "<div style='width:36px;height:2px;background-color:#b8956a;margin:14px auto 0;'></div>" +
            "</td></tr>" +

            "<tr><td style='height:3px;background:linear-gradient(90deg,#6b4e10,#c9a96e,#e8d5a3,#c9a96e,#6b4e10);" +
            "font-size:0;line-height:3px;'>&nbsp;</td></tr>" +

            // BODY
            "<tr><td style='padding:42px 48px 36px;'>" +
            "<p style='margin:0 0 24px;color:#333333;font-size:15px;line-height:1.7;'>" +
            "Um novo usu&aacute;rio acaba de se cadastrar no sistema.</p>" +

            "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'" +
            " style='border:1px solid #ede8e0;border-radius:6px;'>" +
            "<tr><td style='padding:14px 18px;border-bottom:1px solid #ede8e0;'>" +
            "<span style='display:block;font-size:11px;color:#999999;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px;'>Nome</span>" +
            "<span style='color:#111111;font-size:15px;font-weight:600;'>" + nomeSeguro + "</span>" +
            "</td></tr>" +
            "<tr><td style='padding:14px 18px;border-bottom:1px solid #ede8e0;'>" +
            "<span style='display:block;font-size:11px;color:#999999;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px;'>E-mail</span>" +
            "<span style='color:#111111;font-size:15px;font-weight:600;'>" + email + "</span>" +
            "</td></tr>" +
            "<tr><td style='padding:14px 18px;border-bottom:1px solid #ede8e0;'>" +
            "<span style='display:block;font-size:11px;color:#999999;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px;'>Forma de cadastro</span>" +
            "<span style='color:#111111;font-size:15px;font-weight:600;'>" + providerLabel + "</span>" +
            "</td></tr>" +
            "<tr><td style='padding:14px 18px;'>" +
            "<span style='display:block;font-size:11px;color:#999999;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px;'>Quando</span>" +
            "<span style='color:#111111;font-size:15px;font-weight:600;'>" + quando + "</span>" +
            "</td></tr>" +
            "</table>" +

            "</td></tr>" +

            // FOOTER
            "<tr><td style='background-color:#111111;padding:24px 48px;text-align:center;'>" +
            "<p style='margin:0 0 6px;color:#b8956a;font-size:10px;letter-spacing:3px;" +
            "text-transform:uppercase;font-weight:500;'>La Femme Atel&icirc;</p>" +
            "<p style='margin:0;color:#555555;font-size:11px;line-height:1.7;'>" +
            "&copy; " + ano + " Todos os direitos reservados &nbsp;&middot;&nbsp; Notifica&ccedil;&atilde;o autom&aacute;tica</p>" +
            "</td></tr>" +

            "</table>" +

            "</td></tr></table>" +
            "</body></html>";
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
