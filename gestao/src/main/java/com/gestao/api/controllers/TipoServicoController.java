package com.gestao.api.controllers;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.gestao.api.controllers.DTOs.TipoServicoDTO;
import com.gestao.api.services.TipoServicoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tipos-servico")
public class TipoServicoController {

    private final TipoServicoService tipoServicoService;

    public TipoServicoController(TipoServicoService tipoServicoService) {
        this.tipoServicoService = tipoServicoService;
    }

    @PostMapping
    public ResponseEntity<TipoServicoDTO> criarTipoServico(@Valid @RequestBody TipoServicoDTO tipoServicoDTO) {
        TipoServicoDTO novoTipo = tipoServicoService.criarTipoServico(tipoServicoDTO);
         URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(novoTipo.id()).toUri();
        return ResponseEntity.created(location).body(novoTipo);
    }

    @GetMapping
    public ResponseEntity<List<TipoServicoDTO>> listarTodosTiposServico() {
         List<TipoServicoDTO> tipos = tipoServicoService.listarTodosTiposServico();
         return ResponseEntity.ok(tipos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoServicoDTO> buscarTipoServicoPorId(@PathVariable Long id) {
        return tipoServicoService.buscarTipoServicoPorId(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new com.gestao.api.services.exceptions.ResourceNotFoundException("Tipo de Serviço não encontrado com id: " + id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TipoServicoDTO> atualizarTipoServico(@PathVariable Long id, @Valid @RequestBody TipoServicoDTO tipoServicoDTO) {
        TipoServicoDTO tipoAtualizado = tipoServicoService.atualizarTipoServico(id, tipoServicoDTO);
        return ResponseEntity.ok(tipoAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTipoServico(@PathVariable Long id) {
        tipoServicoService.deletarTipoServico(id);
        return ResponseEntity.noContent().build();
    }
}