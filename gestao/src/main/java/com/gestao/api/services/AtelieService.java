package com.gestao.api.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.AtelieDTO;
import com.gestao.api.entities.Atelie;
import com.gestao.api.entities.Usuario;

@Service
public class AtelieService {

    private final DAOController daoController;

    public AtelieService(DAOController daoController) {
        this.daoController = daoController;
    }

    @Transactional
    public AtelieDTO obterDoUsuarioLogado() {
        return AtelieDTO.refactor(obterOuCriar(UserContext.getIdUsuario()));
    }

    @Transactional
    public AtelieDTO atualizar(AtelieDTO dto) {
        Atelie a = obterOuCriar(UserContext.getIdUsuario());

        if (dto.nome() != null)                 a.setNome(blankToNull(dto.nome()));
        if (dto.logo() != null)                 a.setLogo(blankToNull(dto.logo()));
        if (dto.endereco() != null)             a.setEndereco(blankToNull(dto.endereco()));
        if (dto.cnpjCpf() != null)              a.setCnpjCpf(blankToNull(dto.cnpjCpf()));
        if (dto.contatoPublico() != null)       a.setContatoPublico(blankToNull(dto.contatoPublico()));
        if (dto.horarioFuncionamento() != null) a.setHorarioFuncionamento(blankToNull(dto.horarioFuncionamento()));

        return AtelieDTO.refactor(daoController.update(a));
    }

    private Atelie obterOuCriar(Long usuarioId) {
        List<Atelie> existentes = daoController
                .select()
                .from(Atelie.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, usuarioId)
                .limit(1)
                .list();

        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }

        Atelie novo = new Atelie();
        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(usuarioId);
        novo.setUsuario(usuarioRef);
        return daoController.insert(novo);
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
