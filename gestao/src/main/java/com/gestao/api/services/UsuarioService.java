package com.gestao.api.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.controllers.DTOs.RegistroUsuarioRequestDTO;
import com.gestao.api.db.DAOController;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.RoleEnum;
import com.gestao.api.select.Select;
import com.gestao.api.services.exceptions.BusinessException;

@Service
public class UsuarioService {

    private final DAOController daoController;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(DAOController daoController, PasswordEncoder passwordEncoder) {
        this.daoController = daoController;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void registrarNovoUsuario(RegistroUsuarioRequestDTO registroDTO) {
        if (registroDTO == null) {
            throw new BusinessException("Dados de registro inválidos.");
        }

        if (registroDTO.email() == null) {
            throw new BusinessException("Email é obrigatório.");
        }

        String emailNormalizado = registroDTO.email().toLowerCase().trim();

        if (emailNormalizado.isBlank()) {
            throw new BusinessException("Email é obrigatório.");
        }

        if (Select.validarEmailExistente(daoController, emailNormalizado)) {
            throw new BusinessException("Este email já está cadastrado.");
        }

        validarSenha(registroDTO.senha());

        if (registroDTO.nome() == null || registroDTO.nome().trim().isEmpty()) {
            throw new BusinessException("Nome é obrigatório.");
        }

        Usuario user = new Usuario();
        user.setNome(registroDTO.nome().trim());
        user.setEmail(emailNormalizado);
        user.setSenha(passwordEncoder.encode(registroDTO.senha()));
//        user.setRole(RoleEnum.ROLE_USER);

        salvar(user);
    }

    // ===================== HELPERS =====================

    @Transactional
    private void salvar(Usuario user) {
        if (user.getId() != null) {
            daoController.update(user);
        } else {
            daoController.insert(user);
        }
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < 8) {
            throw new BusinessException("Senha inválida.");
        }

        if (senha.length() > 128) {
            throw new BusinessException("Senha muito grande.");
        }

        // precisa ter: número, maiúscula e minúscula
        if (!senha.matches(".*[A-Z].*") ||
            !senha.matches(".*[a-z].*")) {
            throw new BusinessException("Senha fraca.");
        }
    }
}
