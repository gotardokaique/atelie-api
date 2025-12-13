package com.gestao.api.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.gen.core.api.AbstractController;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.MethodMapping;
import com.gen.core.api.HttpMethod;

import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.ServicoService;
import com.gestao.api.services.exceptions.BusinessException;

@EndpointMapping("/api/v1/servicos")
public class ServicoController extends AbstractController {

    private final ServicoService servicoService;

    public ServicoController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @MethodMapping(type = HttpMethod.POST)
    public void criarServico(@RequestBody ServicoRequestDTO requestDTO) {
        servicoService.criarServico(requestDTO);
        setMessageSuccess("Serviço criado com sucesso.");
    }

    @MethodMapping(path = "/em-aberto", type = HttpMethod.GET)
    public ResponseEntity<List<ServicoResponseDTO>> listarServicosEmAberto() {
        return ResponseEntity.ok(servicoService.listarServicosEmAberto());
    }

    @MethodMapping(path = "/finalizados", type = HttpMethod.GET)
    public ResponseEntity<List<ServicoResponseDTO>> listarServicosFinalizados() {
        return ResponseEntity.ok(servicoService.listarServicosFinalizados());
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.GET)
    public ResponseEntity<ServicoResponseDTO> buscarServicoPorId(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(servicoService.buscarServicoPorId(id));
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.PUT)
    public void atualizarServicoCompleto(
            @PathVariable("id") Long id,
            @RequestBody ServicoRequestDTO requestDTO) {
        servicoService.atualizarServicoCompleto(id, requestDTO);
        setMessageSuccess("Serviço atualizado com sucesso.");
    }

    @MethodMapping(path = "/{id}/status-servico", type = HttpMethod.PATCH)
    public void atualizarStatusServico(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> updates) {

        String statusStr = updates.get("statusServico");
        if (statusStr == null) {
            throw new BusinessException("Campo 'statusServico' é obrigatório.");
        }

        StatusServico novoStatus;
        try {
            novoStatus = StatusServico.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Valor inválido para 'statusServico': " + statusStr);
        }

        servicoService.atualizarStatusServico(id, novoStatus);
        setMessageSuccess("Status do serviço atualizado.");
    }

    @MethodMapping(path = "/{id}/status-pagamento", type = HttpMethod.PATCH)
    public void atualizarStatusPagamento(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> updates) {

        String statusStr = updates.get("statusPagamento");
        if (statusStr == null) {
            throw new BusinessException("Campo 'statusPagamento' é obrigatório.");
        }

        StatusPagamento novoStatus;
        try {
            novoStatus = StatusPagamento.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Valor inválido para 'statusPagamento': " + statusStr);
        }

        servicoService.atualizarStatusPagamento(id, novoStatus);
        setMessageSuccess("Status de pagamento atualizado.");
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.DELETE)
    public void deletarServico(@PathVariable("id") Long id) {
        servicoService.deletarServico(id);
        setMessageSuccess("Serviço removido com sucesso.");
    }
}
