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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gestao.api.controllers.DTOs.DespesaDTO;
import com.gestao.api.controllers.DTOs.DespesaTotalDTO;
import com.gestao.api.services.DespesaService;

@RestController
@RequestMapping("/api/v1/despesas")
public class DespesaController {

    private final DespesaService despesaService;

    public DespesaController(DespesaService despesaService) {
        this.despesaService = despesaService;
    }

    @PostMapping
    public ResponseEntity<Void> adicionarDespesa(@RequestBody DespesaDTO dto) {
        despesaService.adicionarDespesa(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<DespesaDTO>> listarDespesas() {
        return ResponseEntity.ok(despesaService.listarDespesas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DespesaDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(despesaService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> atualizarDespesa(@PathVariable Long id, @RequestBody DespesaDTO dto) {
        despesaService.atualizarDespesa(id, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarDespesa(@PathVariable Long id) {
        despesaService.deletarDespesa(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/total")
    public ResponseEntity<DespesaTotalDTO> calcularTotalMes(
            @RequestParam(required = false) String mesAno) {
        return ResponseEntity.ok(despesaService.calcularTotalMes(mesAno));
    }
}
