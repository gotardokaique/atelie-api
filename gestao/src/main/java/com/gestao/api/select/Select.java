package com.gestao.api.select;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.QueryBuilder;
import com.gen.core.db.TransactionDB;
import com.gen.core.db.exception.NotFoundException;
import com.gestao.api.entities.Usuario;

public class Select {
	public static boolean validarEmailExistente(DAOController dao, String email) {

		try {
			Usuario usuario = dao.select()
					.from(Usuario.class)
					.where("email", Condicao.EQUAL, email)
					.one();

			return true;
		} catch (Exception not) {
		}

		return false;

	}

	public static Usuario buscarUsuarioPorEmail(TransactionDB trans, String email) {
		try {
			Usuario usuario = new QueryBuilder(trans).select()
					.from(Usuario.class)
					.where("email", Condicao.EQUAL, email)
					.one();

			return usuario;
		} catch (Exception not) {
			throw new NotFoundException("Usuario não encontrado");
		}
	}

}
