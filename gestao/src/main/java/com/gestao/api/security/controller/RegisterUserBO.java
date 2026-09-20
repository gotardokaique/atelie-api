package com.gestao.api.security.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.gen.core.bo.AbstractRegisterBO;
import com.gen.core.bo.EmailBO;
import com.gen.core.db.Condicao;
import com.gen.core.db.QueryBuilder;
import com.gen.core.db.TransactionDB;
import com.gen.core.security.exception.BusinessException;
import com.gen.core.utils.StringEncryptUtils;
import com.gestao.api.bo.TemplateEmailStr;
import com.gestao.api.controllers.DTOs.GoogleAuthRequest;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.ProviderUsuario;
import com.gestao.api.select.Select;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class RegisterUserBO extends AbstractRegisterBO {

    private final TransactionDB trans;
    private final EmailBO emailBO;
    private final StringEncryptUtils stringEncryptUtils;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.admin.email:kaiquecgotardo@gmail.com}")
    private String adminEmail;

    public RegisterUserBO(TransactionDB trans, EmailBO emailBO, StringEncryptUtils stringEncryptUtils) {
        this.trans = trans;
        this.emailBO = emailBO;
        this.stringEncryptUtils = stringEncryptUtils;
    }

    @Override
    protected void persistirNovoUsuario(String nome, String email, String senhaHash) {
        trans.insert(new Usuario(nome, email, senhaHash));
    }

    @Override
    protected void aposCadastro(String nome, String email) {
        notificarAdminNovoUsuario(nome, email, ProviderUsuario.LOCAL);
    }

    @Override
    protected Optional<ResponseEntity<?>> validarAntesDoLogin(String email) {
        try {
            Usuario usuarioCheck = new QueryBuilder(trans).select()
                    .from(Usuario.class)
                    .where("email", Condicao.EQUAL, email)
                    .one();

            if (usuarioCheck.getProvider() == ProviderUsuario.GOOGLE) {
                return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message",
                                "Esta conta usa login com Google. Entre pelo botão do Google.")));
            }
        } catch (Exception ignorado) {
        }

        return Optional.empty();
    }

    @Override
    protected String mensagemBloqueio() {
        return "Parece que você esqueceu sua senha... Tome um café e refresque a mente.";
    }

    @Override
    public void processarRedefinirSenha(String email) {
        try {
            Usuario usuario = Select.buscarUsuarioPorEmail(trans, email);
            String token = redefinirSenhaService.generateToken(usuario);
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
            String resetLink = frontendUrl + "/reset-password?token=" + encodedToken;

            String saudacao = (usuario.getNome() != null && usuario.getNome().isBlank() == false)
                    ? ", " + usuario.getNome() : "";
            String html = TemplateEmailStr.montar(TemplateEmailStr.RESET_SENHA_LINK, Map.of(
                    "saudacao", saudacao,
                    "linkResetSenha", resetLink,
                    "ano", String.valueOf(Year.now().getValue())));

            emailBO.criar().remetente().destinatario(email)
                    .mensagem(html)
                    .titulo("Redefinição de Senha — Gestão Ateliê")
                    .enviar();

        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar token");
        }
    }

    public ResponseEntity<?> autenticarComGoogle(GoogleAuthRequest request, HttpServletResponse response)
            throws Exception {
        String email = request.getEmail().trim().toLowerCase();

        Usuario usuario = null;
        try {
            usuario = new QueryBuilder(trans).select()
                    .from(Usuario.class)
                    .where("googleId", Condicao.EQUAL, request.getGoogleId())
                    .one();
        } catch (Exception ignorado) {
        }

        if (usuario == null) {
            Usuario porEmail = null;
            try {
                porEmail = new QueryBuilder(trans).select()
                        .from(Usuario.class)
                        .where("email", Condicao.EQUAL, email)
                        .one();
            } catch (Exception ignorado) {
            }

            if (porEmail != null) {
                throw new BusinessException("Este email já está cadastrado com senha. Faça login com email e senha.");
            }

            var novoUsuario = new Usuario();
            novoUsuario.setEmail(email);
            novoUsuario.setNome(request.getNome());
            novoUsuario.setGoogleId(request.getGoogleId());
            novoUsuario.setProvider(ProviderUsuario.GOOGLE);
            novoUsuario.setSenha(stringEncryptUtils.encrypt(request.getGoogleId()));
            novoUsuario.setAtivo(true);
            trans.insert(novoUsuario);

            usuario = new QueryBuilder(trans).select()
                    .from(Usuario.class)
                    .where("googleId", Condicao.EQUAL, request.getGoogleId())
                    .one();

            notificarAdminNovoUsuario(request.getNome(), email, ProviderUsuario.GOOGLE);
        }

        return emitirAutenticacao(usuario, response);
    }

    private void notificarAdminNovoUsuario(String nome, String email, ProviderUsuario provider) {
        try {
            if (adminEmail == null || adminEmail.isBlank()) {
                logger.warn("Notificação de novo usuário ignorada: admin email não configurado.");
                return;
            }

            String providerLabel = provider == ProviderUsuario.GOOGLE ? "Google" : "Email/Senha";
            String titulo = "Novo cadastro no Gestão Ateliê — " + (nome == null ? email : nome);
            String nomeSeguro = (nome == null || nome.isBlank()) ? "(sem nome)" : nome;
            String quando = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            String corpo = TemplateEmailStr.montar(TemplateEmailStr.NOVO_USUARIO_ADMIN, Map.of(
                    "nome", nomeSeguro,
                    "email", email,
                    "provider", providerLabel,
                    "dataHora", quando,
                    "ano", String.valueOf(Year.now().getValue())));

            emailBO.criar()
                    .remetente()
                    .destinatario(adminEmail)
                    .titulo(titulo)
                    .mensagem(corpo)
                    .enviar();

            logger.info("Notificação de novo usuário enviada ao admin ({}) — provider={}", adminEmail, providerLabel);
        } catch (Exception e) {
            logger.warn("Falha ao notificar admin sobre novo usuário {} ({}): {}", email, provider, e.getMessage());
        }
    }
}
