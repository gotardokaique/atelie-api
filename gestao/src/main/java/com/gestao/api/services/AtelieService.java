package com.gestao.api.services;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.security.exception.BusinessException;
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

        if (dto.dataAberturaMei() != null)      a.setDataAberturaMei(converterData(dto.dataAberturaMei()));
        if (dto.nomeResponsavel() != null)      a.setNomeResponsavel(blankToNull(dto.nomeResponsavel()));
        if (dto.cidade() != null)               a.setCidade(blankToNull(dto.cidade()));
        if (dto.uf() != null)                   a.setUf(converterUf(dto.uf()));

        return AtelieDTO.refactor(daoController.update(a));
    }

    private LocalDate converterData(String texto) {
        if (texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("Data de abertura do MEI inválida. Use o formato AAAA-MM-DD.");
        }
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

    private String converterUf(String texto) {
        String uf = blankToNull(texto);
        if (uf == null) {
            return null;
        }
        if (uf.matches("[A-Za-z]{2}") == false) {
            throw new BusinessException("UF inválida. Use a sigla do estado com 2 letras, por exemplo SP.");
        }
        return uf.toUpperCase();
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
