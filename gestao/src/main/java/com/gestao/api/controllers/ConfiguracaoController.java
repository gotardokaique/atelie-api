package com.gestao.api.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gestao.api.controllers.DTOs.ConfiguracaoDTO;
import com.gestao.api.services.ConfiguracaoService;

@RestController
@RequestMapping("/api/v1/configuracoes")
public class ConfiguracaoController extends AbstractController {

    private final ConfiguracaoService configuracaoService;

    public ConfiguracaoController(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    @GetMapping
    public ResponseEntity<ConfiguracaoDTO> obter() {
        return ResponseEntity.ok(configuracaoService.obterDoUsuarioLogado());
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoDTO> atualizar(@RequestBody ConfiguracaoDTO dto) {
        return ResponseEntity.ok(configuracaoService.atualizar(dto));
    }
}
