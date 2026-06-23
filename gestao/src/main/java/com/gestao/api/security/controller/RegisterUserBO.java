package com.gestao.api.security.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
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
import com.gen.core.db.DAOController;
import com.gen.core.db.QueryBuilder;

import com.gen.core.db.TransactionDB;
import com.gen.core.db.exception.NotFoundException;
import com.gen.core.security.SessionService;
import com.gen.core.security.TokenService;
import com.gen.core.utils.HttpUtils;
import com.gen.core.utils.StringEncryptUtils;
import com.gestao.api.bo.EmailBO;
import com.gestao.api.bo.TemplateEmailStr;
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

    private TentativaLogin carregarTentativaEmail(String email) {
        return carregarTentativaPorChave(gerarChaveTentativaEmail(email));
    }

    private void salvarTentativaEmail(String email, TentativaLogin tentativa) {
        salvarTentativaPorChave(gerarChaveTentativaEmail(email), tentativa);
    }

    private void resetTentativaEmail(String email) {
        resetTentativaPorChave(gerarChaveTentativaEmail(email));
    }

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

    public ResponseEntity<?> cadastrarEAutenticar(String nome, String email, String hashed,
            HttpServletResponse response) throws Exception {
        var usuario = new Usuario(nome, email, hashed);
        trans.insert(usuario);

        Usuario persistido = new QueryBuilder(trans).select()
                .from(Usuario.class)
                .where("email", Condicao.EQUAL, email)
                .one();

        notificarAdminNovoUsuario(nome, email, ProviderUsuario.LOCAL);

        return emitirAutenticacao(persistido, response);
    }

    private ResponseEntity<?> emitirAutenticacao(Usuario usuario, HttpServletResponse response) throws Exception {
   	 List<String> roles = Select.rolesDoUsuario(usuario.getId(), new DAOController(trans));

        var jwt = tokenService.generateToken(usuario, roles);
        sessionService.storeToken(usuario.getId(), jwt);
        HttpUtils.addSecureCookie(response, "auth_token", jwt, (int) (jwtExpirationMs / 1000), cookieDomain);
        return ResponseEntity.ok(new LoginResponseDTO(jwt));
    }

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

            ResponseEntity<?> resposta = emitirAutenticacao(user, response);

            logger.info("Login bem-sucedido para {} (IP: {})", email, clientIp);
            return resposta;

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

            String saudacao = (usuario.getNome() != null && !usuario.getNome().isBlank())
                    ? ", " + usuario.getNome() : "";
            String html = TemplateEmailStr.montar(TemplateEmailStr.RESET_SENHA_LINK, Map.of(
                    "saudacao", saudacao,
                    "linkResetSenha", resetLink,
                    "ano", String.valueOf(java.time.Year.now().getValue())
            ));

            emailBO.criar().remetente().destinatario(email)
                    .mensagem(html)
                    .titulo("Redefinição de Senha — Gestão Ateliê")
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

        // 5. Emite a autenticação pela MESMA via do login/registro: cookie
        // HttpOnly auth_token com o MESMO Domain (cookieDomain) + body. Antes
        // usava addSecureCookie sem domain (host-only em api.<dominio>), o que
        // impedia o front (apex) de enxergar o cookie e quebrava a sessão.
        return emitirAutenticacao(usuario, response);
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
            String nomeSeguro = (nome == null || nome.isBlank()) ? "(sem nome)" : nome;
            String quando = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            String corpo = TemplateEmailStr.montar(TemplateEmailStr.NOVO_USUARIO_ADMIN, Map.of(
                    "nome", nomeSeguro,
                    "email", email,
                    "provider", providerLabel,
                    "dataHora", quando,
                    "ano", String.valueOf(java.time.Year.now().getValue())
            ));

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
