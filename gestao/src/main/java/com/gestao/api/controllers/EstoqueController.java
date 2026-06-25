package com.gestao.api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gestao.api.controllers.DTOs.EstoqueDTO;
import com.gestao.api.services.EstoqueService;

@RestController
@RequestMapping("/api/v1/estoque")
public class EstoqueController extends AbstractController {

    private final EstoqueService estoqueService;

    public EstoqueController(EstoqueService estoqueService) {
        this.estoqueService = estoqueService;
    }

    @PostMapping
    public ResponseEntity<Void> adicionarItemEstoque(@RequestBody EstoqueDTO estoqueDTO) {
        estoqueService.adicionarItemEstoque(estoqueDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<EstoqueDTO>> listarTodoEstoque() {
        return ResponseEntity.ok(estoqueService.listarTodoEstoque());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstoqueDTO> buscarItemEstoquePorId(@PathVariable Long id) {
        return ResponseEntity.ok(estoqueService.buscarItemEstoquePorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> atualizarItemEstoque(@PathVariable Long id, @RequestBody EstoqueDTO estoqueDTO) {
        estoqueService.atualizarItemEstoque(id, estoqueDTO);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarItemEstoque(@PathVariable Long id) {
        estoqueService.deletarItemEstoque(id);
        return ResponseEntity.noContent().build();
    }
}
