package com.gestao.api.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.db.DAOController;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.services.exceptions.BusinessException;
import com.gestao.api.services.exceptions.NotFoundException;
import com.gestao.api.services.exceptions.ResourceNotFoundException;


@Service
public class PessoaService {

    private final DAOController daoController;

    public PessoaService(DAOController daoController) {
        this.daoController = daoController;
    }

    // ===================== CRIAR =====================

    @Transactional
    public void criarPessoa(PessoaDTO dto) {

        String nomeLimpo = limparNome(dto.nome());
        String telefoneLimpo = limparTelefone(dto.telefone());
        String medidasLimpas = limparMedidas(dto.medidas());

        validarDadosPessoa(nomeLimpo, telefoneLimpo);

        Pessoa pessoa = new Pessoa();
        pessoa.setNome(nomeLimpo);
        pessoa.setTelefone(telefoneLimpo);
        pessoa.setMedidas(medidasLimpas);

        salvar(pessoa);

    }

    // ===================== LISTAR =====================

    @Transactional(readOnly = true)
    public List<PessoaDTO> listarTodasPessoas() {
        List<Pessoa> pessoas = daoController
                .select()
                .from(Pessoa.class)
                .list();

        return PessoaDTO.refactor(pessoas);
    }

    // ===================== BUSCAR POR ID =====================

    @Transactional(readOnly = true)
    public PessoaDTO buscarPessoaPorId(Long id) {
    	Pessoa pessoa;
        try {
            pessoa = daoController
                    .select()
                    .from(Pessoa.class)
                    .id(id);

            return PessoaDTO.refactor(pessoa);
        } catch (NotFoundException e) {
        	pessoa = new Pessoa();
        	
            return PessoaDTO.refactor(pessoa);
        }
    }

    // ===================== ATUALIZAR =====================

    @Transactional
    public void atualizarPessoa(Long id, PessoaDTO dto) {

        Pessoa pessoaExistente = buscarPessoaById(id);

        String nomeLimpo = limparNome(dto.nome());
        String telefoneLimpo = limparTelefone(dto.telefone());
        String medidasLimpas = limparMedidas(dto.medidas());

        validarDadosPessoa(nomeLimpo, telefoneLimpo);

        if (nomeLimpo != null) {
            pessoaExistente.setNome(nomeLimpo);
        }

        pessoaExistente.setTelefone(telefoneLimpo);
        pessoaExistente.setMedidas(medidasLimpas);

        salvar(pessoaExistente);

    }

    // ===================== DELETAR =====================

    @Transactional
    public void deletarPessoa(Long id) {
        Pessoa pessoaExistente = buscarPessoaById(id);
        daoController.delete(pessoaExistente);
    }

    // ===================== HELPERS PRIVADOS =====================

    private Pessoa buscarPessoaById(Long id) {
        try {
            return daoController
                    .select()
                    .from(Pessoa.class)
                    .id(id);
        } catch (NotFoundException e) {
            throw new ResourceNotFoundException("Pessoa não encontrada com id: " + id);
        }
    }

    private String limparNome(String nome) {
        if (nome == null) return null;
        return nome.trim();
    }

    private String limparTelefone(String telefone) {
        if (telefone == null) return null;
        return telefone.replaceAll("[^0-9]", "");
    }

    private String limparMedidas(String medidas) {
        if (medidas == null) return null;
        return medidas.trim();
    }

    private void validarDadosPessoa(String nomeLimpo, String telefoneLimpo) {

        if (nomeLimpo != null && !StringUtils.hasText(nomeLimpo)) {
            throw new BusinessException("O nome da pessoa não pode ser composto apenas por espaços.");
        }

        if (!StringUtils.hasText(telefoneLimpo)) {
            throw new BusinessException("Telefone é obrigatório.");
        }

        if (!telefoneLimpo.matches("^[0-9]+$")) {
            throw new BusinessException("Telefone deve conter apenas números.");
        }

        if (telefoneLimpo.length() < 8 || telefoneLimpo.length() > 15) {
            throw new BusinessException("Telefone deve ter entre 8 e 15 dígitos.");
        }
    }

    // ===================== SALVAR (INSERT / UPDATE) =====================

    @Transactional
    public Pessoa salvar(Pessoa pessoa) {
        if (pessoa.getId() != null) {
            return daoController.update(pessoa);
        } else {
            return daoController.insert(pessoa);
        }
    }
}
