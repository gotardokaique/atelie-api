package com.gestao.api.security.controller;

import org.springframework.stereotype.Service;

import com.gestao.api.db.Condicao;
import com.gestao.api.db.QueryBuilder;
import com.gestao.api.db.TransactionDB;
import com.gestao.api.entities.Usuario;

import jakarta.persistence.NoResultException;

@Service
public class UsuarioServiceValidacao {

    private final TransactionDB trans;

    public UsuarioServiceValidacao(TransactionDB trans) {
        this.trans = trans;
    }

    public Boolean validarEmailJaCadastrado(String email) {
        try {
            new QueryBuilder(trans)
                    .select()
                    .from(Usuario.class)
                    .where("email", Condicao.EQUAL, email.toLowerCase())
                    .one();
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }

}
