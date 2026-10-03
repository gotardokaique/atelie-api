package com.gestao.api.controllers;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gestao.api.services.PrazoNotificacaoJob;
import com.gestao.api.services.PrazoNotificacaoJob.ResumoExecucao;

/** Disparo manual do job. Só existe com developer=true (nunca em produção). */
@RestController
@RequestMapping("/api/v1/notificacoes/job")
@ConditionalOnProperty(name = "developer", havingValue = "true")
public class NotificacaoJobController extends AbstractController {

    private final PrazoNotificacaoJob job;
    private final Clock clock;

    public NotificacaoJobController(PrazoNotificacaoJob job, Clock clock) {
        this.job = job;
        this.clock = clock;
    }

    @PostMapping("/prazos")
    public ResponseEntity<ResumoExecucao> executarPrazos() {
        return ResponseEntity.ok(job.executar(clock.instant(), false));
    }
}
