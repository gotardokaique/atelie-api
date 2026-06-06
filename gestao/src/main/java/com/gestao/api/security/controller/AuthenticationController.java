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
import com.gestao.api.controllers.DTOs.UserMeDTO;
import com.gestao.api.entities.Usuario;
import com.gestao.api.security.redefinir.dto.PasswordResetInput;

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
    public ResponseEntity<?> register(@RequestBody @Valid RegistroUsuarioRequestDTO data) {
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

        boolean cadastrado = registerBO.cadastrarUsuario(nome, email, passwordEncoder.encode(senha), null);
        if (!cadastrado) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("message", "Hmm... algo deu errado, verifique sua conexão.."));
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(java.util.Map.of("message", "Cadastro recebido. Se os dados estiverem corretos, você receberá uma confirmação."));
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
        return ResponseEntity.ok(new UserMeDTO(user.getNome(), user.getEmail()));
    }

    @MethodMapping(path = "/logout", type = RequestMethod.POST)
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Usuario user = (Usuario) UserContext.getUsuarioAutenticado();
        sessionService.removeToken(user.getId());
        HttpUtils.removeCookie(response, "auth_token");
        return ResponseEntity.ok("Logout executado.");
    }
}
