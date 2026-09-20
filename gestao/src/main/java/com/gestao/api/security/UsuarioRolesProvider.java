package com.gestao.api.security;

import java.util.List;

import org.springframework.stereotype.Component;

import com.gen.core.contracts.RolesProvider;
import com.gen.core.contracts.UserAccount;
import com.gen.core.db.DAOController;
import com.gestao.api.select.Select;

@Component
public class UsuarioRolesProvider implements RolesProvider {

    private final DAOController daoController;

    public UsuarioRolesProvider(DAOController daoController) {
        this.daoController = daoController;
    }

    @Override
    public List<String> rolesForLogin(UserAccount user) {
        return Select.rolesDoUsuario(user.getId(), daoController);
    }
}
