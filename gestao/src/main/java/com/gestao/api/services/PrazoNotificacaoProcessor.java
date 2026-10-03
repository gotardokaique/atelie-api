package com.gestao.api.services;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.gestao.api.entities.Notificacao;
import com.gestao.api.services.PrazoNotificacaoPlanner.Planejada;
import com.gestao.api.services.PrazoNotificacaoPlanner.Preferencias;
import com.gestao.api.services.PrazoNotificacaoPlanner.ServicoPrazo;

@Component
public class PrazoNotificacaoProcessor {

    private final PrazoNotificacaoPlanner planner;
    private final NotificacaoStore store;

    public PrazoNotificacaoProcessor(PrazoNotificacaoPlanner planner, NotificacaoStore store) {
        this.planner = planner;
        this.store = store;
    }

    /** Grava só o que ainda não existe; devolve as notificações novas (as que vão para o push). */
    public List<Notificacao> processar(Long usuarioId, Preferencias preferencias, List<ServicoPrazo> servicos, Instant agora) {
        return planner.planejar(usuarioId, preferencias, servicos, agora).stream()
                .map(p -> store.salvarSeNova(usuarioId, p))
                .flatMap(Optional::stream)
                .toList();
    }

    public List<Planejada> simular(Long usuarioId, Preferencias preferencias, List<ServicoPrazo> servicos, Instant agora) {
        return planner.planejar(usuarioId, preferencias, servicos, agora);
    }

    public boolean jaExiste(Planejada planejada) {
        return store.existe(planejada.chaveDedupe());
    }
}
