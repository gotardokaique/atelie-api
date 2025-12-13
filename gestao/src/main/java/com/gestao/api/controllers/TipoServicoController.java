package com.gestao.api.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.gen.core.api.AbstractController;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.MethodMapping;
import com.gen.core.api.HttpMethod;

import com.gestao.api.controllers.DTOs.TipoServicoDTO;
import com.gestao.api.services.TipoServicoService;

@EndpointMapping("/api/v1/tipos-servico")
public class TipoServicoController extends AbstractController {

    private final TipoServicoService tipoServicoService;

    public TipoServicoController(TipoServicoService tipoServicoService) {
        this.tipoServicoService = tipoServicoService;
    }

    @MethodMapping(type = HttpMethod.POST)
    public void criarTipoServico(TipoServicoDTO tipoServicoDTO) {
        tipoServicoService.criarTipoServico(tipoServicoDTO);
        setMessageSuccess("Tipo de serviço criado com sucesso.");
    }

    @MethodMapping(type = HttpMethod.GET)
    public ResponseEntity<List<TipoServicoDTO>> listarTodosTiposServico() {
        List<TipoServicoDTO> tipos = tipoServicoService.listarTodosTiposServico();
        return ResponseEntity.ok(tipos);
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.GET)
    public ResponseEntity<TipoServicoDTO> buscarTipoServicoPorId(Long id) {
        TipoServicoDTO dto = tipoServicoService.buscarTipoServicoPorId(id);
        return ResponseEntity.ok(dto);
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.PUT)
    public void atualizarTipoServico(Long id, TipoServicoDTO tipoServicoDTO) {
        tipoServicoService.atualizarTipoServico(id, tipoServicoDTO);
        setMessageSuccess("Tipo de serviço atualizado com sucesso.");
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.DELETE)
    public void deletarTipoServico(Long id) {
        tipoServicoService.deletarTipoServico(id);
        setMessageSuccess("Tipo de serviço removido com sucesso.");
    }
}
