package com.gestao.api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gen.core.db.filter.FilterQuery;
import com.gestao.api.controllers.DTOs.ClienteDetalhesDTO;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.services.PessoaService;

@RestController
@RequestMapping("/api/v1/pessoas")
public class PessoaController {

    private final PessoaService pessoaService;

    public PessoaController(PessoaService pessoaService) {
        this.pessoaService = pessoaService;
    }

    @PostMapping
    public ResponseEntity<Void> criarPessoa(@RequestBody PessoaDTO pessoaDTO) throws Exception {
        pessoaService.criarPessoa(pessoaDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
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
    public ResponseEntity<Void> atualizarPessoa(@PathVariable Long id, @RequestBody PessoaDTO pessoaDTO) {
        pessoaService.atualizarPessoa(id, pessoaDTO);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPessoa(@PathVariable Long id) {
        pessoaService.deletarPessoa(id);
        return ResponseEntity.noContent().build();
    }
}
