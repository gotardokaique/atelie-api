package com.gestao.api.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.ServicoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/servicos")
public class ServicoController {

    private final ServicoService servicoService;

    public ServicoController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @PostMapping
    public ResponseEntity<ServicoResponseDTO> criarServico (@RequestBody ServicoRequestDTO requestDTOList) {
        ServicoResponseDTO novosServico = servicoService.criarServico(requestDTOList);
       
        return ResponseEntity.status(HttpStatus.CREATED).body(novosServico);
    }

    @GetMapping("/em-aberto")
    public ResponseEntity<List<ServicoResponseDTO>> listarServicosEmAberto() {
        List<ServicoResponseDTO> servicos = servicoService.listarServicosEmAberto();
        return ResponseEntity.ok(servicos);
    }

    @GetMapping("/finalizados")
    public ResponseEntity<List<ServicoResponseDTO>> listarServicosFinalizados() {
        List<ServicoResponseDTO> servicos = servicoService.listarServicosFinalizados();
        return ResponseEntity.ok(servicos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServicoResponseDTO> buscarServicoPorId(@PathVariable Long id) {
        return servicoService.buscarServicoPorId(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new com.gestao.api.services.exceptions.ResourceNotFoundException("Serviço não encontrado com id: " + id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServicoResponseDTO> atualizarServicoCompleto(@PathVariable Long id, @Valid @RequestBody ServicoRequestDTO requestDTO) {
         ServicoResponseDTO servicoAtualizado = servicoService.atualizarServicoCompleto(id, requestDTO);
         return ResponseEntity.ok(servicoAtualizado);
    }

    @PatchMapping("/{id}/status-servico")
    public ResponseEntity<ServicoResponseDTO> atualizarStatusServico(@PathVariable Long id, @RequestBody Map<String, String> updates) throws Exception {
         String statusStr = updates.get("statusServico");
         if (statusStr == null) {
             throw new com.gestao.api.services.exceptions.BusinessException("Campo 'statusServico' é obrigatório para esta operação.");
         }
         try {
             StatusServico novoStatus = StatusServico.valueOf(statusStr.toUpperCase());
             ServicoResponseDTO servicoAtualizado = servicoService.atualizarStatusServico(id, novoStatus);
             return ResponseEntity.ok(servicoAtualizado);
         } catch (IllegalArgumentException e) {
             throw new com.gestao.api.services.exceptions.BusinessException("Valor inválido para 'statusServico': " + statusStr);
         }
         // ResourceNotFoundException será lançada pelo serviço se o ID não existir
    }

     @PatchMapping("/{id}/status-pagamento")
    public ResponseEntity<ServicoResponseDTO> atualizarStatusPagamento(@PathVariable Long id, @RequestBody Map<String, String> updates) {
         String statusStr = updates.get("statusPagamento");
          if (statusStr == null) {
             throw new com.gestao.api.services.exceptions.BusinessException("Campo 'statusPagamento' é obrigatório para esta operação.");
         }
         try {
             StatusPagamento novoStatus = StatusPagamento.valueOf(statusStr.toUpperCase());
             ServicoResponseDTO servicoAtualizado = servicoService.atualizarStatusPagamento(id, novoStatus);
             return ResponseEntity.ok(servicoAtualizado);
         } catch (IllegalArgumentException e) {
            throw new com.gestao.api.services.exceptions.BusinessException("Valor inválido para 'statusPagamento': " + statusStr);
         }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarServico(@PathVariable Long id) {
         servicoService.deletarServico(id);
         return ResponseEntity.noContent().build();
    }
}