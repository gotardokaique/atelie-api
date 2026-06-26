package com.gestao.api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gen.core.api.ApiResponse;
import com.gestao.api.controllers.DTOs.FechamentoResumoDTO;
import com.gestao.api.controllers.DTOs.FinalizarServicoRequestDTO;
import com.gestao.api.controllers.DTOs.MaterialConsumidoDTO;
import com.gestao.api.controllers.DTOs.ServicoProdutoDTO;
import com.gestao.api.controllers.DTOs.ServicoProdutoRequestDTO;
import com.gestao.api.services.ServicoProdutoService;

import jakarta.validation.Valid;

/**
 * Extensão de estoque do serviço (produtos vendidos + fechamento com materiais).
 * Compartilha o base path com o {@code ServicoController}, sem colidir com suas rotas.
 */
@RestController
@RequestMapping("/api/v1/servicos")
public class ServicoEstoqueController extends AbstractController {

    private final ServicoProdutoService servicoProdutoService;

    public ServicoEstoqueController(ServicoProdutoService servicoProdutoService) {
        this.servicoProdutoService = servicoProdutoService;
    }

    @PostMapping("/{servicoId}/produtos")
    public ResponseEntity<ApiResponse<ServicoProdutoDTO>> vincularProduto(@PathVariable Long servicoId,
            @Valid @RequestBody ServicoProdutoRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(servicoProdutoService.vincularProduto(servicoId, req),
                        "Produto vinculado com sucesso."));
    }

    @GetMapping("/{servicoId}/produtos")
    public ResponseEntity<List<ServicoProdutoDTO>> listarProdutos(@PathVariable Long servicoId) {
        return ResponseEntity.ok(servicoProdutoService.listarProdutos(servicoId));
    }

    @DeleteMapping("/{servicoId}/produtos/{servicoProdutoId}")
    public ResponseEntity<Void> removerProduto(@PathVariable Long servicoId,
            @PathVariable Long servicoProdutoId) {
        servicoProdutoService.removerProduto(servicoId, servicoProdutoId);
        return ResponseEntity.noContent().build();
    }

    /** Materiais pré-preenchidos pela ficha técnica (sugestão, editável no front). */
    @GetMapping("/{servicoId}/materiais-sugeridos")
    public ResponseEntity<List<MaterialConsumidoDTO>> materiaisSugeridos(@PathVariable Long servicoId) {
        return ResponseEntity.ok(servicoProdutoService.materiaisSugeridos(servicoId));
    }

    @PostMapping("/{servicoId}/finalizar")
    public ResponseEntity<ApiResponse<FechamentoResumoDTO>> finalizar(@PathVariable Long servicoId,
            @Valid @RequestBody FinalizarServicoRequestDTO req) {
        return ResponseEntity.ok(ApiResponse.ok(servicoProdutoService.finalizar(servicoId, req),
                "Serviço finalizado com sucesso."));
    }

    @GetMapping("/{servicoId}/resumo")
    public ResponseEntity<FechamentoResumoDTO> resumo(@PathVariable Long servicoId) {
        return ResponseEntity.ok(servicoProdutoService.resumo(servicoId));
    }
}
