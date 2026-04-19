package com.gestao.api.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.controllers.DTOs.TipoServicoDTO;
import com.gestao.api.context.UserContext;
import com.gen.core.db.DAOController;
import com.gen.core.db.Condicao;
import com.gestao.api.entities.TipoServico;
import com.gestao.api.entities.Usuario;
import com.gestao.api.services.exceptions.NotFoundException;
import com.gestao.api.services.exceptions.ResourceNotFoundException;


@Service
public class TipoServicoService {

    private final DAOController daoController;

    public TipoServicoService(DAOController daoController) {
        this.daoController = daoController;
    }

    // ===================== CRIAR =====================

    @Transactional
    public void criarTipoServico(TipoServicoDTO dto) {
        TipoServico tipo = new TipoServico();
        tipo.setNome(dto.nome());
        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(UserContext.getIdUsuario());
        tipo.setUsuario(usuarioRef);
        salvar(tipo);
    }

    // ===================== LISTAR =====================

    @Transactional(readOnly = true)
    public List<TipoServicoDTO> listarTodosTiposServico() {
        List<TipoServico> tipos = daoController
                .select()
                .from(TipoServico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .orderBy("nome", true)
                .list();

        return TipoServicoDTO.refactor(tipos);
    }

    // ===================== BUSCAR POR ID =====================

    @Transactional(readOnly = true)
    public TipoServicoDTO buscarTipoServicoPorId(Long id) {
        TipoServico tipo;
        try {
            tipo = daoController
                    .select()
                    .from(TipoServico.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);
            return TipoServicoDTO.refactor(tipo);
        } catch (NotFoundException e) {
            tipo = new TipoServico();
            return TipoServicoDTO.refactor(tipo);
        }
    }

    // ===================== ATUALIZAR =====================

    @Transactional
    public void atualizarTipoServico(Long id, TipoServicoDTO dto) {
        TipoServico tipoExistente = buscarTipoServicoById(id);
        tipoExistente.setNome(dto.nome());
        salvar(tipoExistente);
    }

    // ===================== DELETAR =====================

    @Transactional
    public void deletarTipoServico(Long id) {
        TipoServico tipo = buscarTipoServicoById(id);
        daoController.delete(tipo);
    }

    // ===================== HELPERS PRIVADOS =====================

    private TipoServico buscarTipoServicoById(Long id) {
        try {
            return daoController
                    .select()
                    .from(TipoServico.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);
        } catch (NotFoundException e) {
            throw new ResourceNotFoundException("Tipo de Serviço não encontrado com id: " + id);
        }
    }

    // ===================== SALVAR =====================

    @Transactional
    public TipoServico salvar(TipoServico tipo) {
        if (tipo.getId() != null) {
            return daoController.update(tipo);
        } else {
            return daoController.insert(tipo);
        }
    }
}
