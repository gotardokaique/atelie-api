package com.gestao.api.security.controller;

import java.time.LocalDateTime;
import java.util.Map;

import javax.naming.AuthenticationException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.gestao.api.controllers.DTOs.LoginResponseDTO;
import com.gestao.api.db.TransactionDB;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.RoleEnum;
import com.gestao.api.security.controller.AuthenticationController;
import com.gestao.api.security.controller.SessionService;
import com.gestao.api.security.controller.TokenService;
import com.gestao.api.security.controller.UsuarioRepository;
import com.gestao.api.security.controller.UsuarioServiceValidacao;

@Component
public class RegisterUserBO {
	private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    @Autowired 
    private AuthenticationManager authenticationManager;

    @Autowired 
    private TokenService tokenService;
	@Autowired
	private SessionService sessionService;

	private static final String RESET_CODE_PREFIX = "password:reset:code:";
	private static final String RESET_ATTEMPT_PREFIX = "password:reset:attempt:";
	private static final long CODE_EXPIRATION_MINUTES = 5;
	private static final int MAX_ATTEMPTS = 5;
	private static final long ATTEMPT_BLOCK_MINUTES = 10;

	private static final int MAX_TENTATIVAS_LOGIN = 5;
	private static final long BLOQUEIO_MINUTOS = 2;
	private static final java.util.regex.Pattern REGEX_EMAIL = java.util.regex.Pattern
			.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
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

    private final UsuarioServiceValidacao usuarioServiceValidacao;
    private final TransactionDB trans;

	public RegisterUserBO(UsuarioServiceValidacao usuarioServiceValidacao, TransactionDB trans) {
		this.usuarioServiceValidacao = usuarioServiceValidacao;
		this.trans = trans;
	}

	public Boolean validarSenhaForte(String senha) {
		if (senha == null)
			return false;
		if (senha.length() < 8)
			return false;

		boolean temMaiuscula = senha.matches(".*[A-Z].*");
		boolean temMinuscula = senha.matches(".*[a-z].*");
		boolean temNumero = senha.matches(".*[0-9].*");
	//	boolean temEspecial = senha.matches(".*[^a-zA-Z0-9].*");

		if (!temMaiuscula || !temMinuscula || !temNumero) {
			return false;
		}

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

	public ResponseEntity<?> processarLogin(String emailRaw, String senha) {
	    String email = emailRaw.trim().toLowerCase();

	    if (!REGEX_EMAIL.matcher(email).matches()) {
	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .body(Map.of("message", "Formato de e-mail inválido"));
	    }

	    TentativaLogin tentativa = tentativasLogin.computeIfAbsent(email, k -> new TentativaLogin());
	    tentativa.ultimaTentativa = LocalDateTime.now();

	    if (tentativa.bloqueadoAte != null && tentativa.bloqueadoAte.isAfter(LocalDateTime.now())) {
	        logger.warn("Login bloqueado para {} até {}", email, tentativa.bloqueadoAte);
	        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
	                .body(Map.of("message", "Parece que você esqueceu sua senha... Tome um café e refresque a mente."));
	    }

	    try {
	        var authToken = new UsernamePasswordAuthenticationToken(email, senha);
	        Authentication auth = authenticationManager.authenticate(authToken);
	        Usuario user = (Usuario) auth.getPrincipal();

	        tentativa.tentativas = 0;
	        tentativa.bloqueadoAte = null;

	        var jwt = tokenService.generateToken(user);
            sessionService.storeToken(user.getId(), jwt);

	        logger.info("Login bem-sucedido para {}", email);
	        return ResponseEntity.ok(new LoginResponseDTO(jwt));

	    } catch (BadCredentialsException | UsernameNotFoundException e) {
	        tentativa.tentativas++;
	        if (tentativa.tentativas >= MAX_TENTATIVAS_LOGIN) {
	            tentativa.bloqueadoAte = LocalDateTime.now().plusMinutes(BLOQUEIO_MINUTOS);
	            logger.warn("Conta bloqueada para {} até {}", email, tentativa.bloqueadoAte);
	        }
	        logger.warn("Credenciais inválidas para {}: tentativa {} de {}. Motivo: {}",
	                email, tentativa.tentativas, MAX_TENTATIVAS_LOGIN, e.getMessage());
	        return ResponseEntity
	                .status(HttpStatus.UNAUTHORIZED)
	                .body(Map.of("message", "Usuário ou senha inválidos"));

	    } catch (Exception e) {
	        logger.error("Erro inesperado ao autenticar {}: {}", email, e.getMessage(), e);
	        return ResponseEntity
	                .status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("message", "Erro interno ao tentar autenticar" + e.getMessage()));
	    }
	}
}
