package com.gestao.api.select;

import com.gestao.api.db.Condicao;
import com.gestao.api.db.DAOController;
import com.gestao.api.entities.Usuario;

public class Select {
	public static boolean validarEmailExistente (DAOController dao, String email) {
		
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

}
