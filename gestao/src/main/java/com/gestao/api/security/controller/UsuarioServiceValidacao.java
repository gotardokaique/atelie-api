package com.gestao.api.security.controller;

import org.springframework.stereotype.Service;

import com.gen.core.db.Condicao;
import com.gen.core.db.QueryBuilder;
import com.gen.core.db.TransactionDB;
import com.gen.core.db.exception.NotFoundException;
import com.gestao.api.entities.Usuario;

import jakarta.persistence.NoResultException;

@Service
public class UsuarioServiceValidacao {

    private final TransactionDB trans;

    public UsuarioServiceValidacao(TransactionDB trans) {
        this.trans = trans;
    }

    public Boolean validarEmailJaCadastrado(String email) throws Exception {
        try {
            new QueryBuilder(trans)
                    .select()
                    .from(Usuario.class)
                    .where("email", Condicao.EQUAL, email.toLowerCase())
                    .one();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

}
