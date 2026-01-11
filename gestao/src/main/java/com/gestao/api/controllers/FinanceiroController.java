package com.gestao.api.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestao.api.controllers.DTOs.HorarioPicoDTO;
import com.gestao.api.controllers.DTOs.ResumoFinanceiroDTO;
import com.gestao.api.services.ServicoService;

@RestController
@RequestMapping("/api/v1/financeiro")
public class FinanceiroController {

    private final ServicoService servicoService;

    public FinanceiroController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @GetMapping("/resumo-mes-atual")
    public ResponseEntity<ResumoFinanceiroDTO> getResumoMes() {
        return ResponseEntity.ok(servicoService.getResumoFinanceiroMesAtual());
    }

    @GetMapping("/resumo-semana-atual")
    public ResponseEntity<ResumoFinanceiroDTO> getResumoSemana() {
        return ResponseEntity.ok(servicoService.getResumoFinanceiroUltimos7Dias());
    }

    @GetMapping("/horarios-pico")
    public ResponseEntity<List<HorarioPicoDTO>> getHorariosPico(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes) {

        LocalDate now = LocalDate.now();
        int anoFinal = (ano != null) ? ano : now.getYear();
        int mesFinal = (mes != null) ? mes : now.getMonthValue();

        return ResponseEntity.ok(servicoService.getHorariosDePicoMes(anoFinal, mesFinal));
    }
}
