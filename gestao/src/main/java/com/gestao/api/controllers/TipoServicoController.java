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
import com.gen.core.api.ApiResponse;
import com.gestao.api.controllers.DTOs.TipoServicoDTO;
import com.gestao.api.services.TipoServicoService;

@RestController
@RequestMapping("/api/v1/tipos-servico")
public class TipoServicoController extends AbstractController {

    private final TipoServicoService tipoServicoService;

    public TipoServicoController(TipoServicoService tipoServicoService) {
        this.tipoServicoService = tipoServicoService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> criarTipoServico(@RequestBody TipoServicoDTO tipoServicoDTO) {
        tipoServicoService.criarTipoServico(tipoServicoDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.okMessage("Tipo de serviço criado com sucesso."));
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
    public ResponseEntity<ApiResponse<Void>> atualizarTipoServico(@PathVariable Long id, @RequestBody TipoServicoDTO tipoServicoDTO) {
        tipoServicoService.atualizarTipoServico(id, tipoServicoDTO);
        return ResponseEntity.ok(ApiResponse.okMessage("Tipo de serviço atualizado com sucesso."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTipoServico(@PathVariable Long id) {
        tipoServicoService.deletarTipoServico(id);
        return ResponseEntity.noContent().build();
    }
}
