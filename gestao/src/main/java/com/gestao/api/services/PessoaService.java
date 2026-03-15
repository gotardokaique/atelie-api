package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.ClienteDetalhesDTO;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.controllers.DTOs.ServicoHistoricoDTO;
import com.gestao.api.db.Condicao;
import com.gestao.api.db.DAOController;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.StatusServico;
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
    @CacheEvict(value = { "PESSOAS_TODAS", "PESSOAS_CLIENTES", "PESSOA_BY_ID" }, allEntries = true)
    public void criarPessoa(PessoaDTO dto) throws Exception {

        String nomeLimpo = limparNome(dto.nome());
        String telefoneLimpo = limparTelefone(dto.telefone());
        String medidasLimpas = limparMedidas(dto.medidas());

        validarDadosPessoa(nomeLimpo, telefoneLimpo);
        verificarTelefoneExistente(telefoneLimpo, null);

        Pessoa pessoa = new Pessoa();
        pessoa.setNome(nomeLimpo);
        pessoa.setTelefone(telefoneLimpo);
        pessoa.setMedidas(medidasLimpas);
        // pessoa.setDataCadastro(new LocalDate().now()));

        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(UserContext.getIdUsuario());
        pessoa.setUsuario(usuarioRef);

        salvar(pessoa);
    }

    // ===================== LISTAR =====================

    @Transactional(readOnly = true)
    @Cacheable(value = "PESSOAS_TODAS", key = "T(com.gestao.api.context.UserContext).getIdUsuario()")
    public List<PessoaDTO> listarTodasPessoas() {
        List<Pessoa> pessoas = daoController
                .select()
                .from(Pessoa.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .orderBy("nome", true)
                .list();

        return PessoaDTO.refactor(pessoas);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "PESSOAS_CLIENTES", key = "T(com.gestao.api.context.UserContext).getIdUsuario()")
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
    @Cacheable(value = "PESSOA_BY_ID", key = "T(com.gestao.api.context.UserContext).getIdUsuario() + ':' + #id")
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
    @CacheEvict(value = { "PESSOAS_TODAS", "PESSOAS_CLIENTES", "PESSOA_BY_ID" }, allEntries = true)
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
    @CacheEvict(value = { "PESSOAS_TODAS", "PESSOAS_CLIENTES", "PESSOA_BY_ID" }, allEntries = true)
    public void deletarPessoa(Long id) {
        Pessoa pessoaExistente = buscarPessoaById(id);
        try {
            daoController.delete(pessoaExistente);
        } catch (Exception e) {
            throw new BusinessException("Não é possível deletar um cliente que possui serviços cadastrados.");
        }
    }

    // ===================== DETALHES DO CLIENTE =====================

    @Transactional(readOnly = true)
    public ClienteDetalhesDTO buscarDetalhesCliente(Long id) {
        Pessoa pessoa = buscarPessoaById(id);

        List<Servico> servicos;
        try {
            servicos = daoController
                    .select()
                    .from(Servico.class)
                    .leftJoin("pessoa")
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .where("pessoa.id", Condicao.EQUAL, id)
                    .orderBy("dataCadastro", false)
                    .list();
        } catch (Exception e) {
            servicos = List.of();
        }

        int total = servicos.size();

        BigDecimal totalGasto = servicos.stream()
                .map(Servico::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendentes = servicos.stream()
                .filter(s -> s.getStatusServico() != StatusServico.FINALIZADO)
                .count();

        long concluidos = servicos.stream()
                .filter(s -> s.getStatusServico() == StatusServico.FINALIZADO)
                .count();

        LocalDate ultimoAtendimento = servicos.stream()
                .map(Servico::getDataFinalizacao)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<ServicoHistoricoDTO> historico = ServicoHistoricoDTO.refactor(servicos);

        return ClienteDetalhesDTO.refactor(
                pessoa,
                total,
                totalGasto,
                (int) pendentes,
                (int) concluidos,
                ultimoAtendimento,
                historico);
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
        if (nome == null)
            return null;
        return nome.trim();
    }

    private String limparTelefone(String telefone) {
        if (telefone == null)
            return null;
        return telefone.replaceAll("[^0-9]", "");
    }

    private String limparMedidas(String medidas) {
        if (medidas == null)
            return null;
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

    private void verificarTelefoneExistente(String telefone, Long id) throws Exception {
        List<Pessoa> existing = daoController
                .select()
                .from(Pessoa.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("telefone", Condicao.EQUAL, telefone)
                .list();

        if (existing.isEmpty() == false) {
            throw new BusinessException("Já existe um cliente cadastrado com este número de telefone.");
        }
    }

    // ===================== SALVAR (INSERT / UPDATE) =====================

    @Transactional
    @CacheEvict(value = { "PESSOAS_TODAS", "PESSOAS_CLIENTES", "PESSOA_BY_ID" }, allEntries = true)
    public Pessoa salvar(Pessoa pessoa) {
        if (pessoa.getId() != null) {
            return daoController.update(pessoa);
        } else {
            return daoController.insert(pessoa);
        }
    }

    @Transactional(readOnly = true)
    public int getQtdClientesCadastradosMesAtual() {

        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate fimMes = hoje.withDayOfMonth(hoje.lengthOfMonth());

        List<Pessoa> pessoasMes = daoController
                .select()
                .from(Pessoa.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("dataCadastro", Condicao.GREATER_OR_EQUAL, inicioMes)
                .where("dataCadastro", Condicao.LESS_OR_EQUAL, fimMes)
                .list();

        return pessoasMes.size();
    }
}
