package com.gestao.api.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.gestao.api.entities.Notificacao;
import com.gestao.api.services.PrazoNotificacaoPlanner.Planejada;
import com.gestao.api.services.PrazoNotificacaoPlanner.Preferencias;
import com.gestao.api.services.PrazoNotificacaoPlanner.ServicoAPagar;
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
        return processar(usuarioId, preferencias, servicos, List.of(), agora);
    }

    public List<Notificacao> processar(Long usuarioId, Preferencias preferencias, List<ServicoPrazo> servicos,
            List<ServicoAPagar> aPagar, Instant agora) {
        return simular(usuarioId, preferencias, servicos, aPagar, agora).stream()
                .map(p -> store.salvarSeNova(usuarioId, p))
                .flatMap(Optional::stream)
                .toList();
    }

    public List<Planejada> simular(Long usuarioId, Preferencias preferencias, List<ServicoPrazo> servicos,
            List<ServicoAPagar> aPagar, Instant agora) {
        List<Planejada> planejadas = new ArrayList<>(planner.planejar(usuarioId, preferencias, servicos, agora));
        planejadas.addAll(planner.planejarPagamentos(usuarioId, preferencias, aPagar, agora));
        return planejadas;
    }

    public boolean jaExiste(Planejada planejada) {
        return store.existe(planejada.chaveDedupe());
    }
}
