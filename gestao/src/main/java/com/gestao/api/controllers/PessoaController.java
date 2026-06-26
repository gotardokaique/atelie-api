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
import com.gestao.api.controllers.DTOs.ClienteDetalhesDTO;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.services.PessoaService;

@RestController
@RequestMapping("/api/v1/pessoas")
public class PessoaController extends AbstractController {

    private final PessoaService pessoaService;

    public PessoaController(PessoaService pessoaService) {
        this.pessoaService = pessoaService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> criarPessoa(@RequestBody PessoaDTO pessoaDTO) throws Exception {
        pessoaService.criarPessoa(pessoaDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.okMessage("Cadastro criado com sucesso."));
    }

    @GetMapping
    public ResponseEntity<List<PessoaDTO>> listarTodasPessoas(FilterQuery filter) {
        return ResponseEntity.ok(pessoaService.listarTodasPessoas(filter));
    }

    @GetMapping("/clientes")
    public ResponseEntity<List<PessoaResumoDTO>> listarClientesDoUsuario() {
        return ResponseEntity.ok(pessoaService.listarClientesDoUsuario());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PessoaDTO> buscarPessoaPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pessoaService.buscarPessoaPorId(id));
    }

    @GetMapping("/{id}/detalhes")
    public ResponseEntity<ClienteDetalhesDTO> buscarDetalhesCliente(@PathVariable Long id) {
        return ResponseEntity.ok(pessoaService.buscarDetalhesCliente(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> atualizarPessoa(@PathVariable Long id, @RequestBody PessoaDTO pessoaDTO) {
        pessoaService.atualizarPessoa(id, pessoaDTO);
        return ResponseEntity.ok(ApiResponse.okMessage("Cadastro atualizado com sucesso."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPessoa(@PathVariable Long id) throws Exception {
        pessoaService.deletarPessoa(id);
        return ResponseEntity.noContent().build();
    }
}
