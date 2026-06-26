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
import com.gen.core.db.filter.FilterQuery;
import com.gen.core.api.ApiResponse;
import com.gestao.api.controllers.DTOs.AjusteEstoqueDTO;
import com.gestao.api.controllers.DTOs.EntradaEstoqueDTO;
import com.gestao.api.controllers.DTOs.InsumoDTO;
import com.gestao.api.services.InsumoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/insumos")
public class InsumoController extends AbstractController {

    private final InsumoService insumoService;

    public InsumoController(InsumoService insumoService) {
        this.insumoService = insumoService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InsumoDTO>> criar(@Valid @RequestBody InsumoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(insumoService.criar(dto), "Insumo criado com sucesso."));
    }

    @GetMapping
    public ResponseEntity<List<InsumoDTO>> listar(FilterQuery filter) {
        return ResponseEntity.ok(insumoService.listar(filter));
    }

    @GetMapping("/alertas")
    public ResponseEntity<List<InsumoDTO>> listarAlertas() {
        return ResponseEntity.ok(insumoService.listarAlertas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InsumoDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(insumoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InsumoDTO>> atualizar(@PathVariable Long id, @Valid @RequestBody InsumoDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(insumoService.atualizar(id, dto), "Insumo atualizado com sucesso."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        insumoService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/entrada")
    public ResponseEntity<ApiResponse<InsumoDTO>> registrarEntrada(@PathVariable Long id, @Valid @RequestBody EntradaEstoqueDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(insumoService.registrarEntrada(id, dto), "Entrada registrada com sucesso."));
    }

    @PostMapping("/{id}/ajuste")
    public ResponseEntity<ApiResponse<InsumoDTO>> registrarAjuste(@PathVariable Long id, @Valid @RequestBody AjusteEstoqueDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(insumoService.registrarAjuste(id, dto), "Ajuste registrado com sucesso."));
    }
}
