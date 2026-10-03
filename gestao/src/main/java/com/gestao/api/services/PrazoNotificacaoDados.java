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

import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.PrazoNotificacaoPlanner.Preferencias;
import com.gestao.api.services.PrazoNotificacaoPlanner.ServicoPrazo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Leituras do job, em projeções (sem carregar entidades inteiras de todos os usuários). */
@Component
public class PrazoNotificacaoDados {

    public record UsuarioResumo(Long id, String nome, String email) {}

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Map<Long, List<ServicoPrazo>> servicosAbertosPorUsuario(LocalDate entregaDesde) {
        List<Object[]> linhas = entityManager.createQuery(
                        "SELECT u.id, s.id, s.descricao, p.nome, s.dataEntregaPrevista, s.statusServico, s.dataFinalizacao "
                        + "FROM Servico s JOIN s.usuario u LEFT JOIN s.pessoa p "
                        + "WHERE u.ativo = true AND s.statusServico <> :finalizado "
                        + "AND s.dataFinalizacao IS NULL AND s.dataEntregaPrevista >= :desde",
                        Object[].class)
                .setParameter("finalizado", StatusServico.FINALIZADO)
                .setParameter("desde", entregaDesde)
                .getResultList();

        Map<Long, List<ServicoPrazo>> porUsuario = new LinkedHashMap<>();
        for (Object[] l : linhas) {
            porUsuario.computeIfAbsent((Long) l[0], k -> new ArrayList<>()).add(new ServicoPrazo(
                    (Long) l[1], (String) l[2], (String) l[3], (LocalDate) l[4], (StatusServico) l[5], (LocalDate) l[6]));
        }
        return porUsuario;
    }

    /** Usuário sem linha em configuracoes usa os padrões (alerta ligado, 3 dias, São Paulo). */
    @Transactional(readOnly = true)
    public Map<Long, Preferencias> preferencias(Collection<Long> usuarioIds) {
        Map<Long, Preferencias> mapa = new HashMap<>();
        if (usuarioIds.isEmpty()) {
            return mapa;
        }
        entityManager.createQuery(
                        "SELECT c.usuario.id, c.notificarPrazo, c.diasAntecedenciaPrazo, c.fusoHorario "
                        + "FROM Configuracao c WHERE c.usuario.id IN :ids",
                        Object[].class)
                .setParameter("ids", usuarioIds)
                .getResultList()
                .forEach(l -> mapa.put((Long) l[0], Preferencias.de((Boolean) l[1], (Integer) l[2], (String) l[3])));

        usuarioIds.forEach(id -> mapa.putIfAbsent(id, Preferencias.de(null, null, null)));
        return mapa;
    }

    @Transactional(readOnly = true)
    public Map<Long, UsuarioResumo> usuarios(Collection<Long> usuarioIds) {
        Map<Long, UsuarioResumo> mapa = new HashMap<>();
        if (usuarioIds.isEmpty()) {
            return mapa;
        }
        entityManager.createQuery("SELECT u.id, u.nome, u.email FROM Usuario u WHERE u.id IN :ids", Object[].class)
                .setParameter("ids", usuarioIds)
                .getResultList()
                .forEach(l -> mapa.put((Long) l[0], new UsuarioResumo((Long) l[0], (String) l[1], (String) l[2])));
        return mapa;
    }
}
