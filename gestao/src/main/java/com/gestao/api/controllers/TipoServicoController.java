package com.gestao.api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestao.api.controllers.DTOs.TipoServicoDTO;
import com.gestao.api.services.TipoServicoService;

@RestController
@RequestMapping("/api/v1/tipos-servico")
public class TipoServicoController {

    private final TipoServicoService tipoServicoService;

    public TipoServicoController(TipoServicoService tipoServicoService) {
        this.tipoServicoService = tipoServicoService;
    }

    @PostMapping
    public ResponseEntity<Void> criarTipoServico(@RequestBody TipoServicoDTO tipoServicoDTO) {
        tipoServicoService.criarTipoServico(tipoServicoDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<TipoServicoDTO>> listarTodosTiposServico() {
        return ResponseEntity.ok(tipoServicoService.listarTodosTiposServico());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoServicoDTO> buscarTipoServicoPorId(@PathVariable Long id) {
        return ResponseEntity.ok(tipoServicoService.buscarTipoServicoPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> atualizarTipoServico(@PathVariable Long id, @RequestBody TipoServicoDTO tipoServicoDTO) {
        tipoServicoService.atualizarTipoServico(id, tipoServicoDTO);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTipoServico(@PathVariable Long id) {
        tipoServicoService.deletarTipoServico(id);
        return ResponseEntity.noContent().build();
    }
}
