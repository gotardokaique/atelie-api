package com.gestao.api.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestBody;

import com.gen.core.api.AbstractController;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.MethodMapping;
import com.gen.core.context.UserContext;
import com.gen.core.security.SessionService;
import com.gen.core.utils.HttpUtils;
import com.gestao.api.controllers.DTOs.GoogleAuthRequest;
import com.gestao.api.controllers.DTOs.LoginRequestDTO;
import com.gestao.api.controllers.DTOs.RegistroUsuarioRequestDTO;
import com.gestao.api.controllers.DTOs.ResetPasswordDTO;
import com.gestao.api.controllers.DTOs.UpdateMeDTO;
import com.gestao.api.controllers.DTOs.UserMeDTO;
import com.gestao.api.entities.Usuario;
import com.gestao.api.security.redefinir.dto.PasswordResetInput;
import com.gestao.api.services.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EndpointMapping("/api/v1/auth")
public class AuthenticationController extends AbstractController {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationController.class);

    @Autowired
    private SessionService sessionService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RegisterUserBO registerBO;
    @Autowired
    private UsuarioService usuarioService;

    @MethodMapping(path = "/login", type = RequestMethod.POST, isPublic = true)
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequestDTO dto,
            HttpServletRequest request, HttpServletResponse response) {
        return registerBO.processarLogin(dto.email(), dto.senha(), request, response);
    }

    @MethodMapping(path = "/google", type = RequestMethod.POST, isPublic = true)
    public ResponseEntity<?> loginComGoogle(@RequestBody @Valid GoogleAuthRequest dto,
            HttpServletResponse response) throws Exception {
        return registerBO.autenticarComGoogle(dto, response);
    }

    @MethodMapping(path = "/register", type = RequestMethod.POST, isPublic = true)
    public ResponseEntity<?> register(@RequestBody @Valid RegistroUsuarioRequestDTO data,
            HttpServletResponse response) {
        String email = data.email().trim().toLowerCase();
        String senha = data.senha();
        String nome = data.nome();

        if (email.length() >= 50 || senha.length() >= 70) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("message", "E-mail e ou senha muito longos..."));
        }

        if (registerBO.isEmailJaRegistrado(email)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(java.util.Map.of("message", "Cadastro recebido. Se os dados estiverem corretos, você receberá uma confirmação."));
        }

        if (!registerBO.validarSenhaForte(senha)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("message",
                            "Senha fraca, tente usar caracteres especias, letras maiusculas..."));
        }

        try {
            // Cria o usuário e já o autentica emitindo o MESMO token do login
            // (cookie HttpOnly auth_token + body LoginResponseDTO).
            return registerBO.cadastrarEAutenticar(nome, email, passwordEncoder.encode(senha), response);
        } catch (Exception e) {
            log.error("[SECURITY] Erro ao cadastrar e autenticar {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("message", "Hmm... algo deu errado, verifique sua conexão.."));
        }
    }

    @MethodMapping(path = "/forgot-password", type = RequestMethod.POST, isPublic = true)
    public ResponseEntity<?> forgotPassword(@RequestBody @Valid PasswordResetInput data) {
        try {
            registerBO.processarRedefinirSenha(data.email());
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok("Se o e-mail existir, você receberá um link de redefinição.");
    }

    @MethodMapping(path = "/reset-password", type = RequestMethod.POST, isPublic = true)
    public ResponseEntity<?> resetPassword(@RequestBody @Valid ResetPasswordDTO data) {
        try {
            registerBO.resetarSenha(data.token(), data.newPassword());
            return ResponseEntity.ok("Senha redefinida com sucesso.");
        } catch (RuntimeException e) {
            log.warn("[SECURITY] Erro ao resetar senha: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Link inválido ou expirado.");
        }
    }

    @MethodMapping(path = "/me", type = RequestMethod.GET)
    public ResponseEntity<?> getMe() {
        Usuario user = (Usuario) UserContext.getUsuarioAutenticado();
        String provider = user.getProvider() != null ? user.getProvider().name() : "LOCAL";
        boolean googleVinculado = user.getGoogleId() != null && !user.getGoogleId().isBlank();
        return ResponseEntity.ok(new UserMeDTO(user.getNome(), user.getEmail(), user.getFoto(), provider, googleVinculado));
    }

    @MethodMapping(path = "/me", type = RequestMethod.PUT)
    public ResponseEntity<?> updateMe(@RequestBody UpdateMeDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizarPerfil(dto.nome(), dto.foto()));
    }

    @MethodMapping(path = "/logout", type = RequestMethod.POST)
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Usuario user = (Usuario) UserContext.getUsuarioAutenticado();
        sessionService.removeToken(user.getId());
        HttpUtils.removeCookie(response, "auth_token");
        return ResponseEntity.ok("Logout executado.");
    }

    /**
     * Invalida a sessão do usuário no Redis (chave única por user) e limpa o
     * cookie atual. Efetivamente desloga este e quaisquer outros dispositivos.
     */
    @MethodMapping(path = "/logout-all", type = RequestMethod.POST)
    public ResponseEntity<?> logoutAll(HttpServletResponse response) {
        Usuario user = (Usuario) UserContext.getUsuarioAutenticado();
        sessionService.removeToken(user.getId());
        HttpUtils.removeCookie(response, "auth_token");
        return ResponseEntity.ok(java.util.Map.of("message", "Sessões encerradas em todos os dispositivos."));
    }

    @MethodMapping(path = "/me", type = RequestMethod.DELETE)
    public ResponseEntity<?> excluirConta() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(java.util.Map.of("message", "Exclusão de conta em breve."));
    }
}
