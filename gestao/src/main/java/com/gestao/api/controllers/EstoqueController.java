package com.gestao.api.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.gen.core.api.AbstractController;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.MethodMapping;
import com.gen.core.api.HttpMethod;

import com.gestao.api.controllers.DTOs.EstoqueDTO;
import com.gestao.api.services.EstoqueService;

@EndpointMapping("/api/v1/estoque")
public class EstoqueController extends AbstractController {

    private final EstoqueService estoqueService;

    public EstoqueController(EstoqueService estoqueService) {
        this.estoqueService = estoqueService;
    }

    // =====================================================================
    // CREATE
    // =====================================================================
    @MethodMapping(path = "", type = HttpMethod.POST)
    public void adicionarItemEstoque(EstoqueDTO estoqueDTO) {
        estoqueService.adicionarItemEstoque(estoqueDTO);
        setMessageSuccess("Item adicionado com sucesso!");
    }

    // =====================================================================
    // READ ALL
    // =====================================================================
    @MethodMapping(path = "", type = HttpMethod.GET)
    public ResponseEntity<List<EstoqueDTO>> listarTodoEstoque() {
        List<EstoqueDTO> estoque = estoqueService.listarTodoEstoque();
        return ResponseEntity.ok(estoque);
    }

    // =====================================================================
    // READ BY ID
    // =====================================================================
    @MethodMapping(path = "/{id}", type = HttpMethod.GET)
    public ResponseEntity<EstoqueDTO> buscarItemEstoquePorId(Long id) {

        EstoqueDTO dto = estoqueService.buscarItemEstoquePorId(id);
        if (dto == null) {
            throwValidationError("Item de estoque com ID " + id + " não encontrado.");
        }

        return ResponseEntity.ok(dto);
    }

    // =====================================================================
    // UPDATE
    // =====================================================================
    @MethodMapping(path = "/{id}", type = HttpMethod.PUT)
    public void atualizarItemEstoque(Long id, EstoqueDTO estoqueDTO) {

        boolean atualizado = estoqueService.atualizarItemEstoque(id, estoqueDTO);

        if (!atualizado) {
            throwValidationError("Item de estoque com ID " + id + " não encontrado.");
        }

        setMessageSuccess("Item atualizado com sucesso!");
    }

    // =====================================================================
    // DELETE
    // =====================================================================
    @MethodMapping(path = "/{id}", type = HttpMethod.DELETE)
    public void deletarItemEstoque(Long id) {
        estoqueService.deletarItemEstoque(id);
        setMessageSuccess("Item removido com sucesso!");
    }
}
