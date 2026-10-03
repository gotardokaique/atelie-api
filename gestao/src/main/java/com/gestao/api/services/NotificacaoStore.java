package com.gestao.api.services;

import java.util.Optional;

import com.gestao.api.entities.Notificacao;
import com.gestao.api.services.PrazoNotificacaoPlanner.Planejada;

public interface NotificacaoStore {

    /** Grava se a chave de dedupe ainda não existe; vazio quando já existia. */
    Optional<Notificacao> salvarSeNova(Long usuarioId, Planejada planejada);

    boolean existe(String chaveDedupe);
}
