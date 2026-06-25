package com.gestao.api.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.WhereDB;
import com.gen.core.db.exception.NotFoundException;
import com.gen.core.db.filter.FilterQuery;
import com.gestao.api.bo.EstoqueBO;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.AjusteEstoqueDTO;
import com.gestao.api.controllers.DTOs.EntradaEstoqueDTO;
import com.gestao.api.controllers.DTOs.InsumoDTO;
import com.gestao.api.entities.Insumo;
import com.gestao.api.entities.Usuario;

@Service
public class InsumoService {

    private final DAOController dao;
    private final EstoqueBO estoqueBO;

    public InsumoService(DAOController dao, EstoqueBO estoqueBO) {
        this.dao = dao;
        this.estoqueBO = estoqueBO;
    }

    @Transactional
    public InsumoDTO criar(InsumoDTO dto) {
        Insumo insumo = new Insumo();
        insumo.setDescricao(dto.descricao());
        insumo.setUnidadeMedida(dto.unidadeMedida());
        insumo.setEstoqueMinimo(dto.estoqueMinimo() != null ? dto.estoqueMinimo() : java.math.BigDecimal.ZERO);
        insumo.setAtivo(dto.ativo() == null || dto.ativo());
        // saldo e custoMedio começam em zero: estoque inicial entra via ENTRADA.

        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(UserContext.getIdUsuario());
        insumo.setUsuario(usuarioRef);

        return InsumoDTO.convert(dao.insert(insumo));
    }

    @Transactional(readOnly = true)
    public List<InsumoDTO> listar(FilterQuery filter) {
        WhereDB where = new WhereDB();
        where.add("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario());

        if (filter != null) {
            filter.applyTo(where);
        }

        List<Insumo> insumos;
        try {
            insumos = dao.select()
                    .from(Insumo.class)
                    .join("usuario")
                    .where(where)
                    .orderBy("descricao", true)
                    .list();
        } catch (NotFoundException e) {
            insumos = new ArrayList<>();
        }
        return InsumoDTO.convert(insumos);
    }

    /** Insumos ativos com saldo <= estoque mínimo (base do alerta). */
    @Transactional(readOnly = true)
    public List<InsumoDTO> listarAlertas() {
        List<Insumo> ativos;
        try {
            ativos = dao.select()
                    .from(Insumo.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .where("ativo", Condicao.EQUAL, true)
                    .list();
        } catch (NotFoundException e) {
            ativos = new ArrayList<>();
        }

        List<Insumo> abaixo = new ArrayList<>();
        for (Insumo i : ativos) {
            if (i.getSaldo() != null && i.getEstoqueMinimo() != null
                    && i.getSaldo().compareTo(i.getEstoqueMinimo()) <= 0) {
                abaixo.add(i);
            }
        }
        return InsumoDTO.convert(abaixo);
    }

    @Transactional(readOnly = true)
    public InsumoDTO buscarPorId(Long id) {
        return InsumoDTO.convert(buscarEntity(id));
    }

    /** Carrega o insumo escopado pelo usuário. Lança 404 se não existir. Uso interno/BO. */
    @Transactional(readOnly = true)
    public Insumo buscarEntity(Long id) {
        try {
            return dao.select()
                    .from(Insumo.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);
        } catch (Exception e) {
            throw new NotFoundException("Insumo não encontrado: " + id);
        }
    }

    /** Atualiza só dados cadastrais. Saldo e custo médio nunca são editados aqui. */
    @Transactional
    public InsumoDTO atualizar(Long id, InsumoDTO dto) {
        Insumo insumo = buscarEntity(id);
        insumo.setDescricao(dto.descricao());
        insumo.setUnidadeMedida(dto.unidadeMedida());
        if (dto.estoqueMinimo() != null) {
            insumo.setEstoqueMinimo(dto.estoqueMinimo());
        }
        if (dto.ativo() != null) {
            insumo.setAtivo(dto.ativo());
        }
        return InsumoDTO.convert(dao.update(insumo));
    }

    /** Inativa (soft delete) para preservar a integridade do ledger. */
    @Transactional
    public void inativar(Long id) {
        Insumo insumo = buscarEntity(id);
        insumo.setAtivo(false);
        dao.update(insumo);
    }

    @Transactional
    public InsumoDTO registrarEntrada(Long id, EntradaEstoqueDTO dto) {
        Insumo insumo = buscarEntity(id);
        estoqueBO.entrada(insumo, dto.quantidade(), dto.custoUnitario(), dto.observacao());
        return InsumoDTO.convert(buscarEntity(id));
    }

    @Transactional
    public InsumoDTO registrarAjuste(Long id, AjusteEstoqueDTO dto) {
        Insumo insumo = buscarEntity(id);
        estoqueBO.ajuste(insumo, dto.quantidadeContada(), dto.observacao());
        return InsumoDTO.convert(buscarEntity(id));
    }
}
