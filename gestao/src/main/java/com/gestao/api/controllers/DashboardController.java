package com.gestao.api.controllers;

import com.gestao.api.controllers.DTOs.DashboardStatsDTO; 
import com.gestao.api.services.ServicoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final ServicoService servicoService; 

    public DashboardController(ServicoService servicoService) { 
        this.servicoService = servicoService;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats() {
        DashboardStatsDTO statsDto = servicoService.getDashboardStats();
        return ResponseEntity.ok(statsDto);
    }
}