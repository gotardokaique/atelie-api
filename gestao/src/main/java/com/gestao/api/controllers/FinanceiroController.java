package com.gestao.api.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;

import com.gen.core.api.AbstractController;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.MethodMapping;
import com.gen.core.api.HttpMethod;
import com.gestao.api.controllers.DTOs.HorarioPicoDTO;
import com.gestao.api.controllers.DTOs.ResumoFinanceiroDTO;
import com.gestao.api.services.ServicoService;

@EndpointMapping("/api/v1/financeiro")
public class FinanceiroController extends AbstractController {

    private final ServicoService servicoService;

    public FinanceiroController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @MethodMapping(path = "/resumo-mes-atual", type = HttpMethod.GET)
    public ResponseEntity<ResumoFinanceiroDTO> getResumoMes() {
        ResumoFinanceiroDTO dto = servicoService.getResumoFinanceiroMesAtual();
        return ResponseEntity.ok(dto);
    }

    @MethodMapping(path = "/resumo-semana-atual", type = HttpMethod.GET)
    public ResponseEntity<ResumoFinanceiroDTO> getResumoSemana() {
        ResumoFinanceiroDTO dto = servicoService.getResumoFinanceiroSemanaAtual();
        return ResponseEntity.ok(dto);
    }

    @MethodMapping(path = "/horarios-pico", type = HttpMethod.GET)
    public ResponseEntity<List<HorarioPicoDTO>> getHorariosPico(Integer ano, Integer mes) {
        if (ano == null) ano = LocalDate.now().getYear();
        if (mes == null) mes = LocalDate.now().getMonthValue();

        List<HorarioPicoDTO> horariosPico = servicoService.getHorariosDePicoMes(ano, mes);
        return ResponseEntity.ok(horariosPico);
    }
}
