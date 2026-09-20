package com.gestao.api.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.gen.core.contracts.UserAccountLoader;
import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.exception.NotFoundException;
import com.gestao.api.entities.Usuario;

@Component
public class UsuarioAccountLoader implements UserAccountLoader {

    private final DAOController daoController;

    public UsuarioAccountLoader(DAOController daoController) {
        this.daoController = daoController;
    }

    @Override
    public Optional<Usuario> findByEmail(String email) {
        try {
            return Optional.of(daoController
                    .select()
                    .from(Usuario.class)
                    .where("email", Condicao.EQUAL, email)
                    .one());
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao buscar usuário por email", e);
        }
    }
}
