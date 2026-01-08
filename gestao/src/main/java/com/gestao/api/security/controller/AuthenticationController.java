package com.gestao.api.security.controller;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestao.api.security.controller.RegisterUserBO;
import com.gestao.api.controllers.DTOs.LoginRequestDTO;
import com.gestao.api.controllers.DTOs.LoginResponseDTO;
import com.gestao.api.controllers.DTOs.RegistroUsuarioRequestDTO;
import com.gestao.api.controllers.DTOs.ForgotPasswordDTO;
import com.gestao.api.controllers.DTOs.ResetPasswordDTO;
import com.gestao.api.entities.Usuario;
import com.gestao.api.security.controller.UsuarioRepository;
import com.gestao.api.security.controller.EmailService;
import com.gestao.api.security.controller.UsuarioServiceValidacao;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private UsuarioRepository repository;
    @Autowired private TokenService tokenService;
    @Autowired private SessionService sessionService;
    @Autowired private EmailService emailService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RegisterUserBO registerBO;

    private static final String RESET_CODE_PREFIX       = "password:reset:code:";
    private static final String RESET_ATTEMPT_PREFIX    = "password:reset:attempt:";
    private static final long   CODE_EXPIRATION_MINUTES = 5;
    private static final int    MAX_ATTEMPTS            = 5;
    private static final long   ATTEMPT_BLOCK_MINUTES   = 10;
    
    private static final int MAX_TENTATIVAS_LOGIN = 5;
    private static final long BLOQUEIO_MINUTOS = 2;
    private static final java.util.regex.Pattern REGEX_EMAIL =
        java.util.regex.Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final java.util.Map<String, TentativaLogin> tentativasLogin = new java.util.HashMap<>();

    private static class TentativaLogin {
        int tentativas;
        java.time.LocalDateTime bloqueadoAte;
        java.time.LocalDateTime ultimaTentativa;
        TentativaLogin() {
            this.tentativas = 0;
            this.bloqueadoAte = null;
            this.ultimaTentativa = java.time.LocalDateTime.now();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequestDTO dto) {
        String email = dto.email();
        String senha = dto.senha();
        return registerBO.processarLogin(email, senha);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegistroUsuarioRequestDTO data) {
    	
        String email = data.email().trim().toLowerCase();
        String senha = data.senha();
        String nome = data.nome();    
    	
    	if (email.length() >= 50 || senha.length() >= 70) {
    		return ResponseEntity.status(HttpStatus.CONFLICT)
    		    .body("E-mail e ou senha muito longos...");

    	}
    	
    	
    	boolean jaRegistrado = registerBO.isEmailJaRegistrado(email);
    	
        if (jaRegistrado) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                                 .body("Tente outro e-mail.");
        }
        
        boolean isSenhaValida = registerBO.validarSenhaForte(senha);
        
        if (isSenhaValida == false) {
        	 return ResponseEntity.status(HttpStatus.CONFLICT)
                     .body("Senha fraca, tente usar caracteres especias, letras maiusculas...");
        }

        String hashed = passwordEncoder.encode(senha);
        boolean isCadastradado = registerBO.cadastrarUsuario(nome, email, hashed, null);
        
        if (isCadastradado == false) {
        	return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Hmm... algo deu errado, verifique sua conexão..");
        } 
        
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody @Valid ForgotPasswordDTO data) {

        UserDetails ud = repository.findByEmail(data.email());
        if (ud instanceof Usuario user) {
            String code       = generateRandomCode(6);
            String codeKey    = RESET_CODE_PREFIX + data.email();
            String attemptKey = RESET_ATTEMPT_PREFIX + data.email();
            redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(attemptKey);
            emailService.sendPasswordResetCode(data.email(), code);
        }
        return ResponseEntity.ok("Se o e-mail existir, você receberá um código de redefinição.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody @Valid ResetPasswordDTO data) {
        String email      = data.email();
        String code       = data.code();
        String newPass    = data.newPassword();
        String attemptKey = RESET_ATTEMPT_PREFIX + email;
        String codeKey    = RESET_CODE_PREFIX + email;

        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts == null) attempts = 1L;
        redisTemplate.expire(attemptKey, ATTEMPT_BLOCK_MINUTES + CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);

        if (attempts > MAX_ATTEMPTS) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                 .body("Muitas tentativas. Tente novamente em " + ATTEMPT_BLOCK_MINUTES + " minutos.");
        }

        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null || !stored.equals(code)) {
            return ResponseEntity.badRequest().body("Código inválido ou expirado.");
        }

        UserDetails ud = repository.findByEmail(email);
        if (!(ud instanceof Usuario user)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado.");
        }

        user.setSenha(passwordEncoder.encode(newPass));
        repository.save(user);
        redisTemplate.delete(codeKey);
        redisTemplate.delete(attemptKey);
        sessionService.removeToken(user.getId());

        return ResponseEntity.ok("Senha redefinida com sucesso.");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Usuario user) {
            sessionService.removeToken(user.getId());
            return ResponseEntity.ok("Logout executado.");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não autenticado.");
    }

    private String generateRandomCode(int length) {
        var rnd = new SecureRandom();
        var sb  = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }
}
