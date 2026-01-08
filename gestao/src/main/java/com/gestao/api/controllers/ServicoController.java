package com.gestao.api.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.ServicoService;
import com.gestao.api.services.exceptions.BusinessException;

@RestController
@RequestMapping("/api/v1/servicos")
public class ServicoController {

    private final ServicoService servicoService;

    public ServicoController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @PostMapping
    public ResponseEntity<Void> criarServico(@RequestBody ServicoRequestDTO requestDTO) {
        servicoService.criarServico(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/em-aberto")
    public ResponseEntity<List<ServicoResponseDTO>> listarServicosEmAberto(
            @RequestParam(required = false) Long pessoaId) {
        return ResponseEntity.ok(servicoService.listarServicosEmAberto(pessoaId));
    }

    @GetMapping("/finalizados")
    public ResponseEntity<List<ServicoResponseDTO>> listarServicosFinalizados() {
        return ResponseEntity.ok(servicoService.listarServicosFinalizados());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServicoResponseDTO> buscarServicoPorId(@PathVariable Long id) {
        return ResponseEntity.ok(servicoService.buscarServicoPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> atualizarServicoCompleto(@PathVariable Long id, @RequestBody ServicoRequestDTO requestDTO) {
        servicoService.atualizarServicoCompleto(id, requestDTO);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status-servico")
    public ResponseEntity<Void> atualizarStatusServico(@PathVariable Long id, @RequestBody Map<String, String> updates) {
        String statusStr = updates.get("statusServico");
        if (statusStr == null || statusStr.isBlank()) {
            throw new BusinessException("Campo 'statusServico' é obrigatório.");
        }

        StatusServico novoStatus;
        try {
            novoStatus = StatusServico.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Valor inválido para 'statusServico': " + statusStr);
        }

        servicoService.atualizarStatusServico(id, novoStatus);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status-pagamento")
    public ResponseEntity<Void> atualizarStatusPagamento(@PathVariable Long id, @RequestBody Map<String, String> updates) {
        String statusStr = updates.get("statusPagamento");
        if (statusStr == null || statusStr.isBlank()) {
            throw new BusinessException("Campo 'statusPagamento' é obrigatório.");
        }

        StatusPagamento novoStatus;
        try {
            novoStatus = StatusPagamento.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Valor inválido para 'statusPagamento': " + statusStr);
        }

        servicoService.atualizarStatusPagamento(id, novoStatus);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarServico(@PathVariable Long id) {
        servicoService.deletarServico(id);
        return ResponseEntity.noContent().build();
    }
}
