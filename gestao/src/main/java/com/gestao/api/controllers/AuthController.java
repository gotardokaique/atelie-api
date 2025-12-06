package com.gestao.api.controllers;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.RequestBody;

import com.gen.core.api.AbstractController;
import com.gen.core.api.ApiResponse;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.HttpMethod;
import com.gen.core.api.MethodMapping;
import com.gestao.api.controllers.DTOs.LoginRequestDTO;
import com.gestao.api.controllers.DTOs.LoginResponseDTO;
import com.gestao.api.controllers.DTOs.RegistroUsuarioRequestDTO;
import com.gestao.api.security.JwtTokenProvider;
import com.gestao.api.services.UsuarioService;

@EndpointMapping("/api/v1/auth")
public class AuthController extends AbstractController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UsuarioService usuarioService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider tokenProvider,
            UsuarioService usuarioService) {

        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.usuarioService = usuarioService;
    }

    @MethodMapping(path = "/login", type = HttpMethod.POST)
    public ApiResponse<LoginResponseDTO> login(@RequestBody LoginRequestDTO dto) {

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.senha()));

            var token = tokenProvider.generateToken(authentication);

            setMessageSuccess("Login realizado com sucesso!");

            return responseOk(new LoginResponseDTO(token));

        } catch (AuthenticationException e) {
            setMessageError("Credenciais inválidas.");
            throw e;

        } catch (Exception e) {
            setMessageError("Erro inesperado ao autenticar.");
            throw e;
        }
    }

	    @MethodMapping(path = "/register", type = HttpMethod.POST)
	    public ApiResponse<Void> register(RegistroUsuarioRequestDTO dto) {
	
	        usuarioService.registrarNovoUsuario(dto);
	
	        return responseOkMessage("Usuário registrado com sucesso!");
	    }
	}
