package com.gestao.api.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gen.core.api.ApiResponse;
import com.gestao.api.controllers.DTOs.AtelieDTO;
import com.gestao.api.services.AtelieService;

@RestController
@RequestMapping("/api/v1/atelie")
public class AtelieController extends AbstractController {

    private final AtelieService atelieService;

    public AtelieController(AtelieService atelieService) {
        this.atelieService = atelieService;
    }

    @GetMapping
    public ResponseEntity<AtelieDTO> obter() {
        return ResponseEntity.ok(atelieService.obterDoUsuarioLogado());
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AtelieDTO>> atualizar(@RequestBody AtelieDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(atelieService.atualizar(dto), "Ateliê atualizado com sucesso."));
    }
}
