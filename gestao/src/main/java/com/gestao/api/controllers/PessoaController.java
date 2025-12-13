package com.gestao.api.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.gen.core.api.AbstractController;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.HttpMethod;
import com.gen.core.api.MethodMapping;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.services.PessoaService;

@EndpointMapping("/api/v1/pessoas")
public class PessoaController extends AbstractController {

    private final PessoaService pessoaService;

    public PessoaController(PessoaService pessoaService) {
        this.pessoaService = pessoaService;
    }

    @MethodMapping(type = HttpMethod.POST)
    public void criarPessoa(@RequestBody PessoaDTO pessoaDTO) {
        pessoaService.criarPessoa(pessoaDTO);
        setMessageSuccess("Pessoa criada com sucesso.");
    }

    @MethodMapping(type = HttpMethod.GET)
    public ResponseEntity<List<PessoaDTO>> listarTodasPessoas() {
        return ResponseEntity.ok(pessoaService.listarTodasPessoas());
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.GET)
    public ResponseEntity<PessoaDTO> buscarPessoaPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pessoaService.buscarPessoaPorId(id));
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.PUT)
    public void atualizarPessoa(
            @PathVariable Long id,
            @RequestBody PessoaDTO pessoaDTO) {

        pessoaService.atualizarPessoa(id, pessoaDTO);
        setMessageSuccess("Pessoa atualizada com sucesso.");
    }

    @MethodMapping(path = "/{id}", type = HttpMethod.DELETE)
    public void deletarPessoa(@PathVariable Long id) {
        pessoaService.deletarPessoa(id);
        setMessageSuccess("Pessoa deletada com sucesso.");
    }
}
