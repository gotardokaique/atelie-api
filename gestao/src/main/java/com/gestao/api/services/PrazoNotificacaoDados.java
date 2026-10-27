package com.gestao.api.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gestao.api.entities.Configuracao;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.PrazoNotificacaoPlanner.Preferencias;
import com.gestao.api.services.PrazoNotificacaoPlanner.ServicoAPagar;
import com.gestao.api.services.PrazoNotificacaoPlanner.ServicoPrazo;

/** Leituras do job, todas pelo QueryBuilder do projeto. */
@Component
public class PrazoNotificacaoDados {

    public record UsuarioResumo(Long id, String nome, String email) {}

    private final DAOController dao;

    public PrazoNotificacaoDados(DAOController dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ServicoPrazo>> servicosAbertosPorUsuario(LocalDate entregaDesde) {
        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.ativo", Condicao.EQUAL, true)
                .where("statusServico", Condicao.NOT_EQUAL, StatusServico.FINALIZADO)
                .where("dataEntregaPrevista", Condicao.GREATER_OR_EQUAL, entregaDesde)
                .list();

        Map<Long, List<ServicoPrazo>> porUsuario = new LinkedHashMap<>();
        for (Servico s : servicos) {
            if (s.getDataFinalizacao() != null) {
                continue;
            }
            porUsuario.computeIfAbsent(s.getUsuario().getId(), k -> new ArrayList<>()).add(new ServicoPrazo(
                    s.getId(), s.getDescricao(), nomeDaPessoa(s), s.getDataEntregaPrevista(), s.getStatusServico(),
                    s.getDataFinalizacao()));
        }
        return porUsuario;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ServicoAPagar>> servicosAPagarPorUsuario(LocalDate finalizadoDesde) {
        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.ativo", Condicao.EQUAL, true)
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PENDENTE)
                .where("dataFinalizacao", Condicao.GREATER_OR_EQUAL, finalizadoDesde)
                .list();

        Map<Long, List<ServicoAPagar>> porUsuario = new LinkedHashMap<>();
        for (Servico s : servicos) {
            porUsuario.computeIfAbsent(s.getUsuario().getId(), k -> new ArrayList<>()).add(new ServicoAPagar(
                    s.getId(), s.getDescricao(), nomeDaPessoa(s), s.getValor(), s.getDataFinalizacao()));
        }
        return porUsuario;
    }

    /** Usuário sem linha em configuracoes usa os padrões (alertas ligados, 3 dias, São Paulo). */
    @Transactional(readOnly = true)
    public Map<Long, Preferencias> preferencias(Collection<Long> usuarioIds) {
        Map<Long, Preferencias> mapa = new HashMap<>();
        if (usuarioIds.isEmpty()) {
            return mapa;
        }
        List<Configuracao> configuracoes = dao.select()
                .from(Configuracao.class)
                .join("usuario")
                .where("usuario.id", Condicao.IN, usuarioIds.toArray())
                .list();

        for (Configuracao c : configuracoes) {
            mapa.put(c.getUsuario().getId(), Preferencias.de(
                    c.getNotificarPrazo(), c.getDiasAntecedenciaPrazo(), c.getFusoHorario(),
                    c.getNotificarPagamentoPendente()));
        }

        usuarioIds.forEach(id -> mapa.putIfAbsent(id, Preferencias.de(null, null, null, null)));
        return mapa;
    }

    @Transactional(readOnly = true)
    public Map<Long, UsuarioResumo> usuarios(Collection<Long> usuarioIds) {
        Map<Long, UsuarioResumo> mapa = new HashMap<>();
        if (usuarioIds.isEmpty()) {
            return mapa;
        }
        List<Usuario> usuarios = dao.select()
                .from(Usuario.class)
                .where("id", Condicao.IN, usuarioIds.toArray())
                .list();

        usuarios.forEach(u -> mapa.put(u.getId(), new UsuarioResumo(u.getId(), u.getNome(), u.getEmail())));
        return mapa;
    }

    private static String nomeDaPessoa(Servico s) {
        Pessoa pessoa = s.getPessoa();
        return pessoa == null ? null : pessoa.getNome();
    }
}
