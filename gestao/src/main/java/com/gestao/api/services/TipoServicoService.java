package com.gestao.api.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.controllers.DTOs.TipoServicoDTO;
import com.gestao.api.entities.TipoServico;
import com.gestao.api.services.exceptions.ResourceNotFoundException;
import com.gen.core.db.DAOController;

import jakarta.persistence.NoResultException;

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
        salvar(tipo);
    }

    // ===================== LISTAR =====================

    @Transactional(readOnly = true)
    public List<TipoServicoDTO> listarTodosTiposServico() {
        List<TipoServico> tipos = daoController
                .select()
                .from(TipoServico.class)
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
                    .id(id);
            return TipoServicoDTO.refactor(tipo);
        } catch (NoResultException e) {
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
                    .id(id);
        } catch (NoResultException e) {
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
