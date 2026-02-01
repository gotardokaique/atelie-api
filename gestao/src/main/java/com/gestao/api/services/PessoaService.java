package com.gestao.api.services;

import java.util.Date;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.db.Condicao;
import com.gestao.api.db.DAOController;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Usuario;
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
    @CacheEvict(
            value = { "PESSOAS_TODAS", "PESSOAS_CLIENTES", "PESSOA_BY_ID" },
            allEntries = true
    )
    public void criarPessoa(PessoaDTO dto) {

        String nomeLimpo = limparNome(dto.nome());
        String telefoneLimpo = limparTelefone(dto.telefone());
        String medidasLimpas = limparMedidas(dto.medidas());

        validarDadosPessoa(nomeLimpo, telefoneLimpo);

        Pessoa pessoa = new Pessoa();
        pessoa.setNome(nomeLimpo);
        pessoa.setTelefone(telefoneLimpo);
        pessoa.setMedidas(medidasLimpas);
        pessoa.setDataCadastro(new Date());

        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(UserContext.getIdUsuario());
        pessoa.setUsuario(usuarioRef);

        salvar(pessoa);
    }

    // ===================== LISTAR =====================

    @Transactional(readOnly = true)
    @Cacheable(
            value = "PESSOAS_TODAS",
            key = "T(com.gestao.api.context.UserContext).getIdUsuario()"
    )
    public List<PessoaDTO> listarTodasPessoas() {
        List<Pessoa> pessoas = daoController
                .select()
                .from(Pessoa.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .list();

        return PessoaDTO.refactor(pessoas);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = "PESSOAS_CLIENTES",
            key = "T(com.gestao.api.context.UserContext).getIdUsuario()"
    )
    public List<PessoaResumoDTO> listarClientesDoUsuario() {
        List<Pessoa> pessoas = daoController
                .select("id", "nome")
                .from(Pessoa.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .orderBy("nome", true)
                .list();

        return PessoaResumoDTO.refactor(pessoas);
    }

    // ===================== BUSCAR POR ID =====================

    @Transactional(readOnly = true)
    @Cacheable(
            value = "PESSOA_BY_ID",
            key = "T(com.gestao.api.context.UserContext).getIdUsuario() + ':' + #id"
    )
    public PessoaDTO buscarPessoaPorId(Long id) {
        Pessoa pessoa;
        try {
            pessoa = daoController
                    .select()
                    .from(Pessoa.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);

            return PessoaDTO.refactor(pessoa);
        } catch (NotFoundException e) {
            pessoa = new Pessoa();
            return PessoaDTO.refactor(pessoa);
        }
    }

    // ===================== ATUALIZAR =====================

    @Transactional
    @CacheEvict(
            value = { "PESSOAS_TODAS", "PESSOAS_CLIENTES", "PESSOA_BY_ID" },
            allEntries = true
    )
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
    @CacheEvict(
            value = { "PESSOAS_TODAS", "PESSOAS_CLIENTES", "PESSOA_BY_ID" },
            allEntries = true
    )
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
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
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
    @CacheEvict(
            value = { "PESSOAS_TODAS", "PESSOAS_CLIENTES", "PESSOA_BY_ID" },
            allEntries = true
    )
    public Pessoa salvar(Pessoa pessoa) {
        if (pessoa.getId() != null) {
            return daoController.update(pessoa);
        } else {
            return daoController.insert(pessoa);
        }
    }
    
    
}
