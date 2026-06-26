package com.gestao.api.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gestao.api.controllers.DTOs.MovimentacaoDTO;
import com.gestao.api.enuns.TipoMovimentacao;
import com.gestao.api.services.MovimentacaoService;

@RestController
@RequestMapping("/api/v1/movimentacoes")
public class MovimentacaoController extends AbstractController {

    private final MovimentacaoService movimentacaoService;

    public MovimentacaoController(MovimentacaoService movimentacaoService) {
        this.movimentacaoService = movimentacaoService;
    }

    /** Extrato filtrável por insumo / tipo / período / serviço. */
    @GetMapping
    public ResponseEntity<List<MovimentacaoDTO>> listarExtrato(
            @RequestParam(required = false) Long insumoId,
            @RequestParam(required = false) TipoMovimentacao tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) Long servicoId) {

        return ResponseEntity.ok(
                movimentacaoService.listarExtrato(insumoId, tipo, dataInicio, dataFim, servicoId));
    }
}
