package com.gestao.api.controllers;

import java.time.Clock;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gestao.api.controllers.DTOs.SimulacaoNotificacaoDTO;
import com.gestao.api.services.PrazoNotificacaoJob;

/** Dry-run do job em produção, antes de ligar notificacoes.job.enabled. */
@RestController
@RequestMapping("/api/v1/z_admin/notificacoes")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminNotificacaoController extends AbstractController {

    private final PrazoNotificacaoJob job;
    private final Clock clock;

    public AdminNotificacaoController(PrazoNotificacaoJob job, Clock clock) {
        this.job = job;
        this.clock = clock;
    }

    @GetMapping("/simulacao")
    public ResponseEntity<List<SimulacaoNotificacaoDTO>> simulacao() {
        return ResponseEntity.ok(job.simular(clock.instant()));
    }
}
