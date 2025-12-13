package com.gestao.api.controllers;

import org.springframework.http.ResponseEntity;

import com.gen.core.api.AbstractController;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.MethodMapping;
import com.gen.core.api.HttpMethod;
import com.gestao.api.controllers.DTOs.DashboardStatsDTO;
import com.gestao.api.services.ServicoService;

@EndpointMapping("/api/v1/dashboard")
public class DashboardController extends AbstractController {

    private final ServicoService servicoService;

    public DashboardController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @MethodMapping(path = "/stats", type = HttpMethod.GET)
    public ResponseEntity<DashboardStatsDTO> getStats() {
        DashboardStatsDTO statsDto = servicoService.getDashboardStats();
        return ResponseEntity.ok(statsDto);
    }
}
