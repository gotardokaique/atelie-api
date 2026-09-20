package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.PageResult;
import com.gen.core.db.WhereDB;
import com.gen.core.db.exception.NotFoundException;
import com.gen.core.db.filter.FilterQuery;
import com.gen.core.security.exception.BusinessException;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.ClienteDetalhesDTO;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.controllers.DTOs.ServicoHistoricoDTO;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.StatusServico;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Service
public class PessoaService {

    private final DAOController daoController;

    @PersistenceContext
    private EntityManager entityManager;

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
    public PageResult<PessoaDTO> listarTodasPessoas(FilterQuery filter, int page, int size, LocalDate dataInicio, LocalDate dataFim) {
        WhereDB where = new WhereDB();
        where.add("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario());

        if (filter != null) {
            filter.applyTo(where);
        }

        if (dataInicio != null && dataFim != null) {
            where.add("dataCadastro", Condicao.BETWEEN, dataInicio, dataFim);
        }

        int pageSize = size > 0 ? size : 50;
        int pageNumber = Math.max(page, 0);

        List<Pessoa> pessoas = daoController
                .select()
                .from(Pessoa.class)
                .join("usuario")
                .where(where)
                .orderBy("nome", true)
                .orderBy("telefone", true)
                .page(pageNumber + 1)
                .pageableSize(pageSize)
                .list();

        long totalElements = contarPessoas(where);

        return new PageResult<>(PessoaDTO.refactor(pessoas), pageNumber, pageSize, totalElements);
    }

    // Query de contagem manual: espelha o mesmo WhereDB da listagem paginada
    // (QueryBuilder não expõe count() nem os params já bindados).
    private long contarPessoas(WhereDB where) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(c) FROM Pessoa c JOIN c.usuario usuario ");
        List<Object> params = new ArrayList<>();
        boolean first = true;

        for (WhereDB.WhereItem item : where.getItens()) {
            jpql.append(first ? "WHERE " : "AND ");
            first = false;

            String campo = item.getCampo().contains(".") ? item.getCampo() : "c." + item.getCampo();
            Condicao condicao = item.getCondicao();
            Object[] valores = item.getValores();

            switch (condicao) {
                case BETWEEN -> {
                    jpql.append(campo)
                            .append(" BETWEEN ?").append(params.size() + 1)
                            .append(" AND ?").append(params.size() + 2)
                            .append(" ");
                    params.add(valores[0]);
                    params.add(valores[1]);
                }
                case IN -> {
                    if (valores.length == 0) {
                        jpql.append("1 = 0 ");
                    } else {
                        jpql.append(campo).append(" IN (");
                        for (int i = 0; i < valores.length; i++) {
                            if (i > 0) jpql.append(", ");
                            jpql.append("?").append(params.size() + 1 + i);
                        }
                        jpql.append(") ");
                        params.addAll(Arrays.asList(valores));
                    }
                }
                case LIKE, ILIKE -> {
                    String raw = String.valueOf(valores[0]);
                    String likeVal = raw.contains("%") ? raw : "%" + raw + "%";
                    jpql.append(campo).append(" ").append(condicao.getOperador())
                            .append(" ?").append(params.size() + 1).append(" ");
                    params.add(likeVal);
                }
                default -> {
                    jpql.append(campo).append(" ").append(condicao.getOperador())
                            .append(" ?").append(params.size() + 1).append(" ");
                    params.add(valores[0]);
                }
            }
        }

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        return query.getSingleResult();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "PESSOAS_CLIENTES", key = "T(com.gestao.api.context.UserContext).getIdUsuario()")
    public List<PessoaResumoDTO> listarClientesDoUsuario() {
        List<Pessoa> pessoas = daoController
                .select("id", "nome", "telefone")
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
    public void deletarPessoa(Long id) throws Exception {
        Pessoa pessoaExistente = buscarPessoaById(id);

        try {
            daoController
                    .select("id")
                    .from(Servico.class)
                    .leftJoin("pessoa")
                    .where("pessoa.id", Condicao.EQUAL, id)
                    .one();
            throw new BusinessException("Não é possível deletar um cliente que possui serviços cadastrados.");
        } catch (NotFoundException e) {
            // Nenhum serviço vinculado — pode deletar
        }

        daoController.delete(pessoaExistente);
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
            throw new NotFoundException("Pessoa não encontrada com id: " + id);
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

        if (nomeLimpo != null && StringUtils.hasText(nomeLimpo) == false) {
            throw new BusinessException("O nome da pessoa não pode ser composto apenas por espaços.");
        }

        if (StringUtils.hasText(telefoneLimpo) == false) {
            throw new BusinessException("Telefone é obrigatório.");
        }

        if (telefoneLimpo.matches("^[0-9]+$") == false) {
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
