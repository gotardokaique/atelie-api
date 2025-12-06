package com.gestao.api.select;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.security.exception.NotFoundException;
import com.gestao.api.entities.Usuario;

public class Select {
	public static boolean validarEmailExistente (DAOController dao, String email) {
		
		try {
			Usuario usuario = dao.select()
					.from(Usuario.class)
					.where("email", Condicao.EQUAL, email)
					.one();

			return true;
		} catch (NotFoundException not) {
		}

		return false;
		
	}

}
