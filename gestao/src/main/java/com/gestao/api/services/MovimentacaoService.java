package com.gestao.api.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.QueryBuilder;
import com.gen.core.db.exception.NotFoundException;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.MovimentacaoDTO;
import com.gestao.api.entities.MovimentacaoEstoque;
import com.gestao.api.enuns.TipoMovimentacao;

@Service
public class MovimentacaoService {

    private static final int LIMITE_EXTRATO = 500;

    private final DAOController dao;

    public MovimentacaoService(DAOController dao) {
        this.dao = dao;
    }

    /** Extrato filtrável de movimentações, escopado pelo usuário (via insumo). */
    @Transactional(readOnly = true)
    public List<MovimentacaoDTO> listarExtrato(Long insumoId, TipoMovimentacao tipo,
            LocalDate dataInicio, LocalDate dataFim, Long servicoId) {

        try {
            QueryBuilder qb = dao.select()
                    .from(MovimentacaoEstoque.class)
                    .join("insumo")
                    .where("insumo.usuario.id", Condicao.EQUAL, UserContext.getIdUsuario());

            if (insumoId != null) {
                qb.where("insumo.id", Condicao.EQUAL, insumoId);
            }
            if (tipo != null) {
                qb.where("tipo", Condicao.EQUAL, tipo);
            }
            if (servicoId != null) {
                qb.where("servico.id", Condicao.EQUAL, servicoId);
            }
            if (dataInicio != null && dataFim != null) {
                qb.where("dataMovimentacao", Condicao.BETWEEN,
                        dataInicio.atStartOfDay(), dataFim.atTime(LocalTime.MAX));
            }

            List<MovimentacaoEstoque> movs = qb
                    .orderBy("dataMovimentacao", false)
                    .limit(LIMITE_EXTRATO)
                    .list();

            return MovimentacaoDTO.convert(movs);
        } catch (NotFoundException e) {
            return new ArrayList<>();
        }
    }

    /** SAIDAs vinculadas a um serviço — base do "material gasto na peça". */
    @Transactional(readOnly = true)
    public List<MovimentacaoEstoque> materiaisGastosPorServico(Long servicoId) {
        try {
            return dao.select()
                    .from(MovimentacaoEstoque.class)
                    .join("insumo")
                    .where("servico.id", Condicao.EQUAL, servicoId)
                    .where("insumo.usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .where("tipo", Condicao.EQUAL, TipoMovimentacao.SAIDA)
                    .orderBy("dataMovimentacao", true)
                    .list();
        } catch (NotFoundException e) {
            return new ArrayList<>();
        }
    }
}
