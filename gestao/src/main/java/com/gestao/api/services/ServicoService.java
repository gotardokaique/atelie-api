package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.db.WhereDB;
import com.gen.core.db.filter.FilterQuery;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.DashboardStatsDTO;
import com.gestao.api.controllers.DTOs.FaturamentoServicoPeriodoDTO;
import com.gestao.api.controllers.DTOs.HorarioPicoDTO;
import com.gestao.api.controllers.DTOs.NomeValorDTO;
import com.gestao.api.controllers.DTOs.PessoaRankingDTO;
import com.gestao.api.controllers.DTOs.ResumoFinanceiroDTO;
import com.gestao.api.controllers.DTOs.ResumoPendenciasDTO;
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.controllers.DTOs.ServicosPorMesDTO;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.exceptions.BusinessException;
import com.gestao.api.services.exceptions.NotFoundException;
import com.gestao.api.entities.FichaTecnica;
import com.gestao.api.entities.Produto;
import com.gestao.api.controllers.DTOs.DespesaDTO;
import com.gestao.api.entities.Despesa;
import com.gestao.api.bo.EstoqueBO;

@Service
public class ServicoService {

    private static final Logger log = LoggerFactory.getLogger(ServicoService.class);
    private static final StatusServico FINALIZADO = StatusServico.FINALIZADO;
    private static final String CACHE_SERVICOS_EM_ABERTO = "SERVICOS_EM_ABERTO";

    private final DAOController daoController;
    private final Clock clock;
    private final DespesaService despesaService;
    private final ProdutoService produtoService;
    private final EstoqueBO estoqueBO;

    public ServicoService(DAOController daoController, Clock clock, DespesaService despesaService, ProdutoService produtoService, EstoqueBO estoqueBO) {
        this.daoController = daoController;
        this.clock = clock;
        this.despesaService = despesaService;
        this.produtoService = produtoService;
        this.estoqueBO = estoqueBO;
    }

    @Transactional
    @CacheEvict(value = CACHE_SERVICOS_EM_ABERTO, key = "T(com.gestao.api.context.UserContext).getIdUsuario()")
    public void criarServico(ServicoRequestDTO requestDTO) {
        Pessoa pessoa = null;

        if (requestDTO.pessoaId() != null) {
            pessoa = buscarPessoaById(requestDTO.pessoaId());
        }

        Produto produto = null;
        if (requestDTO.produtoId() != null) {
            produto = produtoService.buscarEntity(requestDTO.produtoId());
        }

        Servico servico = new Servico();
        servico.setPessoa(pessoa);
        servico.setProduto(produto);
        servico.setDescricao(requestDTO.descricao());
        servico.setDataEntregaPrevista(requestDTO.dataEntregaPrevista());
        servico.setValor(requestDTO.valor());
        servico.setUrgente(Boolean.TRUE.equals(requestDTO.urgente()));

        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(UserContext.getIdUsuario());
        servico.setUsuario(usuarioRef);

        LocalDate hoje = LocalDate.now(clock);

        if (servico.getDataEntregaPrevista() != null
                && servico.getDataEntregaPrevista().isBefore(hoje)) {
            throw new BusinessException("A data de previsão de entrega não pode ser no passado.");
        }

        if (servico.isUrgente()) {
            servico.setStatusServico(StatusServico.URGENTE);
        } else {
            servico.setStatusServico(StatusServico.PENDENTE);
        }

        servico.setStatusPagamento(StatusPagamento.PENDENTE);

        salvar(servico);

        if (Boolean.TRUE.equals(requestDTO.geraDespesa()) && servico.getProduto() != null) {
            gerarDespesaEBaixarEstoque(servico, servico.getProduto());
        }
    }

    private void gerarDespesaEBaixarEstoque(Servico servico, Produto produto) {
        try {
            List<FichaTecnica> ficha = produtoService.listarFichaEntity(produto.getId());
            if (ficha == null || ficha.isEmpty()) return;

            BigDecimal custoTotal = BigDecimal.ZERO;
            for (FichaTecnica item : ficha) {
                if (item.getInsumo() != null && item.getQuantidade() != null) {
                    BigDecimal qtd = item.getQuantidade();
                    estoqueBO.saida(item.getInsumo(), qtd, servico, "Baixa automática da OS " + servico.getId());
                    if (item.getInsumo().getCustoMedio() != null) {
                        BigDecimal custoItem = item.getInsumo().getCustoMedio().multiply(qtd);
                        custoTotal = custoTotal.add(custoItem);
                    }
                }
            }

            if (custoTotal.compareTo(BigDecimal.ZERO) > 0) {
                LocalDate dataAtual = LocalDate.now(clock);
                Despesa despesa = new Despesa();
                despesa.setDescricao("Custo de insumos - " + produto.getDescricao() + " (OS " + servico.getId() + ")");
                despesa.setValor(custoTotal);
                despesa.setMes(dataAtual.getMonthValue());
                despesa.setAno(dataAtual.getYear());
                despesa.setUsuario(servico.getUsuario());
                despesa.setServico(servico);
                daoController.insert(despesa);
            }
        } catch (Exception e) {
            log.error("Erro ao gerar despesa de insumos para o produto " + produto.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ServicoResponseDTO> listarServicosEmAberto(FilterQuery filter) {

        WhereDB where = new WhereDB();
        where.add("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario());
        where.add("statusServico", Condicao.IN,
                StatusServico.PENDENTE,
                StatusServico.EM_ANDAMENTO,
                StatusServico.URGENTE);

        if (filter != null) {
            filter.withEntityClass(Servico.class).applyTo(where);
        }

        List<Servico> servicos;
        try {
            servicos = daoController
                    .select()
                    .from(Servico.class)
                    .leftJoin("pessoa")
                    .join("usuario")
                    .where(where)
                    .orderByRaw(
                            "CASE WHEN (c.urgente = true OR c.statusServico = ?) THEN 0 ELSE 1 END ASC",
                            StatusServico.URGENTE)
                    .orderByRaw("CASE WHEN c.dataEntregaPrevista IS NULL THEN 1 ELSE 0 END ASC")
                    .orderByRaw(
                            "CASE WHEN c.dataEntregaPrevista IS NOT NULL AND c.dataEntregaPrevista <= CURRENT_DATE THEN 0 ELSE 1 END ASC")
                    .orderBy("dataEntregaPrevista", true)
                    .orderBy("dataCadastro", true)
                    .limit(200)
                    .list();

        } catch (NotFoundException not) {
            servicos = new ArrayList<>();
        }

        return ServicoResponseDTO.refactor(servicos);
    }

    @Transactional(readOnly = true)
    public List<ServicoResponseDTO> listarServicosFinalizados(FilterQuery filter) {
        WhereDB where = new WhereDB();
        where.add("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario());
        where.add("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO);

        if (filter != null) {
            filter.withEntityClass(Servico.class).applyTo(where);
        }

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .leftJoin("pessoa")
                .join("usuario")
                .where(where)
                .orderBy("statusPagamento", false)
                .orderBy("dataCadastro", false)
                .limit(100)
                .list();

        return ServicoResponseDTO.refactor(servicos);
    }

    @Transactional(readOnly = true)
    public ServicoResponseDTO buscarServicoPorId(Long id) {
        Servico servico;
        try {
            servico = daoController
                    .select()
                    .from(Servico.class)
                    .leftJoin("pessoa")
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);

            return ServicoResponseDTO.refactor(servico);
        } catch (NotFoundException e) {
            servico = new Servico();
            return ServicoResponseDTO.refactor(servico);
        }
    }

    @Transactional
    @CacheEvict(value = CACHE_SERVICOS_EM_ABERTO, key = "T(com.gestao.api.context.UserContext).getIdUsuario()")
    public void atualizarStatusServico(Long id, StatusServico novoStatus) {
        Servico servico = buscarServicoById(id);

        servico.setStatusServico(novoStatus);

        if (FINALIZADO.equals(servico.getStatusServico())) {
            servico.setDataFinalizacao(LocalDate.now(clock));
        }

        salvar(servico);
    }

    @Transactional
    @CacheEvict(value = CACHE_SERVICOS_EM_ABERTO, key = "T(com.gestao.api.context.UserContext).getIdUsuario()")
    public void atualizarStatusPagamento(Long id, StatusPagamento novoStatus) {
        Servico servico = buscarServicoById(id);
        servico.setStatusPagamento(novoStatus);
        salvar(servico);
    }

    @Transactional
    @CacheEvict(value = CACHE_SERVICOS_EM_ABERTO, key = "T(com.gestao.api.context.UserContext).getIdUsuario()")
    public void atualizarServicoCompleto(Long id, ServicoRequestDTO requestDTO) {
        Servico servicoExistente = buscarServicoById(id);

        estoqueBO.estornarSaidasPorServico(servicoExistente);
        despesaService.deletarPorServico(servicoExistente.getId());

        Pessoa pessoa = null;
        if (requestDTO.pessoaId() != null) {
            pessoa = buscarPessoaById(requestDTO.pessoaId());
        }

        Produto produto = null;
        if (requestDTO.produtoId() != null) {
            produto = produtoService.buscarEntity(requestDTO.produtoId());
        }

        servicoExistente.setPessoa(pessoa);
        servicoExistente.setProduto(produto);
        servicoExistente.setDescricao(requestDTO.descricao());
        servicoExistente.setDataEntregaPrevista(requestDTO.dataEntregaPrevista());
        servicoExistente.setValor(requestDTO.valor());
        servicoExistente.setUrgente(Boolean.TRUE.equals(requestDTO.urgente()));

        if (!FINALIZADO.equals(servicoExistente.getStatusServico())) {
            if (servicoExistente.isUrgente()) {
                servicoExistente.setStatusServico(StatusServico.URGENTE);
            } else {
                servicoExistente.setStatusServico(StatusServico.PENDENTE);
            }
        }

        LocalDate hoje = LocalDate.now(clock);

        if (servicoExistente.getDataEntregaPrevista() != null
                && servicoExistente.getDataEntregaPrevista().isBefore(hoje)) {
            throw new BusinessException("A data de previsão de entrega não pode ser no passado.");
        }

        salvar(servicoExistente);

        if (Boolean.TRUE.equals(requestDTO.geraDespesa()) && produto != null) {
            gerarDespesaEBaixarEstoque(servicoExistente, produto);
        }
    }

    @Transactional
    @CacheEvict(value = CACHE_SERVICOS_EM_ABERTO, key = "T(com.gestao.api.context.UserContext).getIdUsuario()")
    public void deletarServico(Long id) {
        Servico servicoExistente = buscarServicoById(id);
        estoqueBO.estornarSaidasPorServico(servicoExistente);
        despesaService.deletarPorServico(servicoExistente.getId());
        daoController.delete(servicoExistente);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularSomaFinalizadosMesAtual() {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);

        LocalDate dataInicio = mesAtual.atDay(1);
        LocalDate dataFim = hoje;

        List<Servico> servicos = buscarServicosFinalizadosEntre(dataInicio, dataFim);
        return somarValor(servicos);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularSomaFinalizadosUltimos7Dias() {
        LocalDate hoje = LocalDate.now(clock);

        LocalDate dataInicio = hoje.minusDays(6);
        LocalDate dataFim = hoje;

        List<Servico> servicos = buscarServicosFinalizadosEntre(dataInicio, dataFim);
        return somarValor(servicos);
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats() {
        // Estoque atual (sem data)
        long pendenteCount = countByStatus(StatusServico.PENDENTE);
        long emAndamentoCount = countByStatus(StatusServico.EM_ANDAMENTO);
        long urgenteCount = countByStatus(StatusServico.URGENTE);

        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);

        LocalDate inicioMesDate = mesAtual.atDay(1);
        LocalDate fimMesDate = mesAtual.atEndOfMonth();

        long finalizadosNoMesCount = buscarServicosFinalizadosEntre(inicioMesDate, fimMesDate).size();

        return new DashboardStatsDTO(
                pendenteCount,
                emAndamentoCount,
                urgenteCount,
                finalizadosNoMesCount);
    }

    @Transactional(readOnly = true)
    private long countByStatusNoMes(StatusServico status, LocalDateTime inicio, LocalDateTime fim) {
        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.EQUAL, status)
                .where("dataCadastro", Condicao.BETWEEN, inicio, fim)
                .list();

        return servicos.size();
    }

    @Transactional(readOnly = true)
    public ResumoFinanceiroDTO getResumoFinanceiroMesAtual() {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);

        LocalDateTime inicio = mesAtual.atDay(1).atStartOfDay();

        List<Servico> servicos = buscarServicosPagosFinalizadosPorDataCadastroEntre(inicio);

        return new ResumoFinanceiroDTO(somarValor(servicos), servicos.size());
    }

    public ResumoFinanceiroDTO getResumoFinanceiroMesAtual(String mesAno) {
        YearMonth ym = parseMesAno(mesAno);
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(LocalTime.MAX);
        List<Servico> servicos = buscarServicosPagosFinalizadosPorDataCadastroEntre(inicio, fim);
        return new ResumoFinanceiroDTO(somarValor(servicos), servicos.size());
    }

    @Transactional(readOnly = true)
    public ResumoFinanceiroDTO getResumoFinanceiroUltimos7Dias() {
        LocalDate hoje = LocalDate.now(clock);

        LocalDateTime inicio = hoje.minusDays(6).atStartOfDay();

        List<Servico> servicos = buscarServicosPagosFinalizadosPorDataCadastroEntre(inicio);

        return new ResumoFinanceiroDTO(somarValor(servicos), servicos.size());
    }

    @Transactional(readOnly = true)
    public List<HorarioPicoDTO> getHorariosDePicoMes(int ano, int mes) {
        YearMonth anoMes = YearMonth.of(ano, mes);

        LocalDateTime dataInicioMes = anoMes.atDay(1).atStartOfDay();
        LocalDateTime dataFimMesMaisUmDia = anoMes.plusMonths(1).atDay(1).atStartOfDay();

        log.info("Buscando horários de pico para o mês: {}/{} ({} a {})",
                mes, ano, dataInicioMes, dataFimMesMaisUmDia);

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("dataCadastro", Condicao.BETWEEN, dataInicioMes, dataFimMesMaisUmDia)
                .list();

        var contagemPorHora = servicos.stream()
                .filter(s -> s.getDataCadastro() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getDataCadastro().getHour(),
                        Collectors.counting()));

        return contagemPorHora.entrySet().stream()
                .map(e -> new HorarioPicoDTO(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(HorarioPicoDTO::hora))
                .toList();
    }

    private Servico buscarServicoById(Long id) {
        try {
            return daoController
                    .select()
                    .from(Servico.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);
        } catch (NotFoundException e) {
            throw new RuntimeException("Serviço não encontrado com id: " + id);
        }
    }

    private Pessoa buscarPessoaById(Long id) {
        Pessoa pes;

        try {
            pes = daoController
                    .select()
                    .from(Pessoa.class)
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .id(id);

        } catch (Exception e) {
            pes = new Pessoa();
        }

        return pes;
    }

    private List<Servico> buscarServicosFinalizadosEntre(LocalDate inicio, LocalDate fim) {
        return daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataFinalizacao", Condicao.BETWEEN, inicio, fim)
                .list();
    }

    private long countByStatus(StatusServico status) {
        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.EQUAL, status)
                .list();

        return servicos.size();
    }

    private long countFinalizadosEntre(LocalDate inicio, LocalDate fim) {
        return buscarServicosFinalizadosEntre(inicio, fim).size();
    }

    private BigDecimal somarValor(List<Servico> servicos) {
        return servicos.stream()
                .map(Servico::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public Servico salvar(Servico servico) {
        if (servico.getId() != null) {
            return daoController.update(servico);
        } else {
            return daoController.insert(servico);
        }
    }

    private List<Servico> buscarServicosPagosFinalizadosPorDataCadastroEntre(LocalDateTime inicio) {
        return daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("dataCadastro", Condicao.GREATER_OR_EQUAL, inicio)
                .list();
    }

    private List<Servico> buscarServicosPagosFinalizadosPorDataCadastroEntre(LocalDateTime inicio, LocalDateTime fim) {
        return daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.BETWEEN, inicio, fim)
                .list();
    }

    @Transactional(readOnly = true)
    public List<NomeValorDTO> listarNomeValorMesAtual() {

        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);
        LocalDateTime inicioMes = mesAtual.atDay(1).atStartOfDay();

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .leftJoin("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.GREATER_OR_EQUAL, inicioMes)
                .orderBy("valor", false)
                .list();

        List<NomeValorDTO> retorno = new ArrayList<>();

        for (Servico ser : servicos) {
            String nome = "Sem pessoa";
            if (ser.getPessoa() != null) {
                nome = ser.getPessoa().getNome();
            }

            BigDecimal valor = ser.getValor();

            retorno.add(new NomeValorDTO(nome, valor));
        }

        return retorno;
    }

    public List<NomeValorDTO> listarNomeValorMesAtual(String mesAno) {
        YearMonth ym = parseMesAno(mesAno);
        LocalDateTime inicioMes = ym.atDay(1).atStartOfDay();
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(LocalTime.MAX);

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .leftJoin("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.BETWEEN, inicioMes, fimMes)
                .orderBy("valor", false)
                .list();

        List<NomeValorDTO> retorno = new ArrayList<>();
        for (Servico ser : servicos) {
            String nome = "Sem pessoa";
            if (ser.getPessoa() != null) {
                nome = ser.getPessoa().getNome();
            }
            BigDecimal valor = ser.getValor();
            retorno.add(new NomeValorDTO(nome, valor));
        }
        return retorno;
    }

    public List<NomeValorDTO> listarNomeValorPendenciasMes(String mesAno) {
        YearMonth ym = parseMesAno(mesAno);
        LocalDateTime inicioMes = ym.atDay(1).atStartOfDay();
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(LocalTime.MAX);

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("statusPagamento", Condicao.NOT_EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.BETWEEN, inicioMes, fimMes)
                .orderBy("valor", false)
                .list();

        List<NomeValorDTO> retorno = new ArrayList<>();
        for (Servico ser : servicos) {
            String nome = "Sem pessoa";
            if (ser.getPessoa() != null) {
                nome = ser.getPessoa().getNome();
            }
            BigDecimal valor = ser.getValor();
            retorno.add(new NomeValorDTO(nome, valor));
        }
        return retorno;
    }

    @Transactional(readOnly = true)
    public long contarPessoasFinalizadasNaoPagasMesAtual() {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);
        LocalDateTime inicioMes = mesAtual.atDay(1).atStartOfDay();

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("statusPagamento", Condicao.NOT_EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.GREATER_OR_EQUAL, inicioMes)
                .list();

        return servicos.stream()
                .map(Servico::getPessoa)
                .filter(Objects::nonNull)
                .map(Pessoa::getId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    @Transactional(readOnly = true)
    public ResumoPendenciasDTO getResumoPendenciasMesAtual() {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);
        LocalDateTime inicioMes = mesAtual.atDay(1).atStartOfDay();

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("statusPagamento", Condicao.NOT_EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.GREATER_OR_EQUAL, inicioMes)
                .list();

        long quantidadePessoas = servicos.stream()
                .map(Servico::getPessoa)
                .filter(Objects::nonNull)
                .map(Pessoa::getId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        BigDecimal valorTotalNaoPago = servicos.stream()
                .map(Servico::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ResumoPendenciasDTO(valorTotalNaoPago, quantidadePessoas);
    }

    public ResumoPendenciasDTO getResumoPendenciasMesAtual(String mesAno) {
        YearMonth ym = parseMesAno(mesAno);
        LocalDateTime inicioMes = ym.atDay(1).atStartOfDay();
        LocalDateTime fimMes = ym.atEndOfMonth().atTime(LocalTime.MAX);

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("statusPagamento", Condicao.NOT_EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.BETWEEN, inicioMes, fimMes)
                .list();

        long quantidadePessoas = servicos.stream()
                .map(Servico::getPessoa)
                .filter(Objects::nonNull)
                .map(Pessoa::getId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        BigDecimal valorTotalNaoPago = servicos.stream()
                .map(Servico::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ResumoPendenciasDTO(valorTotalNaoPago, quantidadePessoas);
    }

    private YearMonth parseMesAno(String mesAno) {
        if (mesAno == null || mesAno.isBlank()) {
            LocalDate hoje = LocalDate.now(clock);
            return YearMonth.from(hoje);
        }
        String valor = mesAno.trim();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yyyy");
        try {
            return YearMonth.parse(valor, fmt);
        } catch (Exception e) {
            DateTimeFormatter alt = DateTimeFormatter.ofPattern("M/yyyy");
            return YearMonth.parse(valor, alt);
        }
    }

    @Transactional(readOnly = true)
    public List<PessoaRankingDTO> rankPessoasUltimos3Meses() {

        LocalDateTime inicio = LocalDateTime.now(clock).minusMonths(3);

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.GREATER_OR_EQUAL, inicio)
                .list();

        Map<Long, PessoaRankingDTO> acumulado = new HashMap<>();
        for (Servico s : servicos) {
            Pessoa p = s.getPessoa();
            if (p == null || p.getId() == null) {
                continue;
            }

            Long pessoaId = p.getId();
            String nome = p.getNome();

            BigDecimal valor = s.getValor() != null
                    ? s.getValor()
                    : BigDecimal.ZERO;

            PessoaRankingDTO dto = acumulado.get(pessoaId);
            if (dto == null) {
                dto = new PessoaRankingDTO(pessoaId, nome, 0L, BigDecimal.ZERO);
                acumulado.put(pessoaId, dto);
            }

            dto.setQuantidade(dto.getQuantidade() + 1);
            dto.setTotal(dto.getTotal().add(valor));
        }

        List<PessoaRankingDTO> ranking = new ArrayList<>();
        for (PessoaRankingDTO dto : acumulado.values()) {
            ranking.add(dto);
        }

        Collections.sort(ranking, (a, b) -> {
            int c1 = Long.compare(b.getQuantidade(), a.getQuantidade());
            if (c1 != 0)
                return c1;

            int c2 = b.getTotal().compareTo(a.getTotal());
            if (c2 != 0)
                return c2;

            return a.getNome().compareToIgnoreCase(b.getNome());
        });

        return ranking.stream()
                .limit(10)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServicoResponseDTO> getServicosProximosPrazo() {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate limite = hoje.plusDays(2);

        List<Servico> todos;
        try {
            todos = daoController
                    .select()
                    .from(Servico.class)
                    .leftJoin("pessoa")
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .where("statusServico", Condicao.IN,
                            StatusServico.PENDENTE,
                            StatusServico.EM_ANDAMENTO,
                            StatusServico.URGENTE)
                    .list();
        } catch (Exception e) {
            return List.of();
        }

        return todos.stream()
                .filter(s -> s.getDataEntregaPrevista() != null)
                .filter(s -> !s.getDataEntregaPrevista().isBefore(hoje))
                .filter(s -> !s.getDataEntregaPrevista().isAfter(limite))
                .map(ServicoResponseDTO::refactor)
                .collect(Collectors.toList());
    }

    public List<Servico> gerarRelatorio() {
        return daoController.select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .list();
    }

    @Transactional(readOnly = true)
    public List<ServicosPorMesDTO> getServicosCriadosUltimos6MesesAgrupado() {

        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);

        // janela: mês atual e mais 5 para trás
        YearMonth inicioYm = mesAtual.minusMonths(5);

        LocalDateTime inicio = inicioYm.atDay(1).atStartOfDay();
        LocalDateTime fimExclusivo = mesAtual.plusMonths(1).atDay(1).atStartOfDay();

        Map<YearMonth, Long> contagem = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            YearMonth ym = inicioYm.plusMonths(i);
            contagem.put(ym, 0L);
        }

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("dataCadastro", Condicao.BETWEEN, inicio, fimExclusivo)
                .list();

        // 3) Agrupa em memória por YearMonth
        for (Servico ser : servicos) {
            if (ser.getDataCadastro() == null)
                continue;
            YearMonth ym = YearMonth.from(ser.getDataCadastro());

            if (ym.isBefore(inicioYm) || ym.isAfter(mesAtual))
                continue;
            contagem.put(ym, contagem.getOrDefault(ym, 0L) + 1L);
        }

        // 4) Formata "MM/yyyy"
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yyyy");

        List<ServicosPorMesDTO> out = new ArrayList<>();
        for (var e : contagem.entrySet()) {
            out.add(new ServicosPorMesDTO(e.getKey().format(fmt), e.getValue()));
        }

        return out;
    }

    /**
     * Faturamento x quantidade de servicos finalizados E pagos, agrupados por periodo.
     * range: "1m" (5 buckets de 7 dias), "3m" (7 buckets de 14 dias),
     *        "6m" (6 buckets mensais, padrao). Bucket pela dataFinalizacao.
     */
    @Transactional(readOnly = true)
    public List<FaturamentoServicoPeriodoDTO> getFaturamentoServicosPorPeriodo(String range) {
        String r = (range == null) ? "6m" : range.toLowerCase();

        switch (r) {
            case "1m":
                return faturamentoPorDias(5, 7);
            case "3m":
                return faturamentoPorDias(7, 14);
            case "6m":
            default:
                return faturamentoMensal(6);
        }
    }

    private List<FaturamentoServicoPeriodoDTO> faturamentoMensal(int meses) {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);
        YearMonth inicioYm = mesAtual.minusMonths(meses - 1L);

        LocalDate inicio = inicioYm.atDay(1);
        LocalDate fim = mesAtual.atEndOfMonth();

        Map<YearMonth, BigDecimal> valorPorMes = new LinkedHashMap<>();
        Map<YearMonth, Long> qtdPorMes = new LinkedHashMap<>();
        for (int i = 0; i < meses; i++) {
            YearMonth ym = inicioYm.plusMonths(i);
            valorPorMes.put(ym, BigDecimal.ZERO);
            qtdPorMes.put(ym, 0L);
        }

        for (Servico s : buscarFinalizadosPagosPorFinalizacaoEntre(inicio, fim)) {
            if (s.getDataFinalizacao() == null)
                continue;
            YearMonth ym = YearMonth.from(s.getDataFinalizacao());
            if (!valorPorMes.containsKey(ym))
                continue;
            BigDecimal v = s.getValor() == null ? BigDecimal.ZERO : s.getValor();
            valorPorMes.put(ym, valorPorMes.get(ym).add(v));
            qtdPorMes.put(ym, qtdPorMes.get(ym) + 1L);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yyyy");
        List<FaturamentoServicoPeriodoDTO> out = new ArrayList<>();
        for (YearMonth ym : valorPorMes.keySet()) {
            out.add(new FaturamentoServicoPeriodoDTO(ym.format(fmt), valorPorMes.get(ym), qtdPorMes.get(ym)));
        }
        return out;
    }

    private List<FaturamentoServicoPeriodoDTO> faturamentoPorDias(int numBuckets, int tamBucketDias) {
        LocalDate hoje = LocalDate.now(clock);
        int totalDias = numBuckets * tamBucketDias;
        LocalDate inicio = hoje.minusDays(totalDias - 1L); // inclui hoje
        LocalDate fim = hoje;

        List<LocalDate> bucketInicios = new ArrayList<>();
        BigDecimal[] valores = new BigDecimal[numBuckets];
        long[] qtds = new long[numBuckets];
        for (int i = 0; i < numBuckets; i++) {
            bucketInicios.add(inicio.plusDays((long) i * tamBucketDias));
            valores[i] = BigDecimal.ZERO;
            qtds[i] = 0L;
        }

        for (Servico s : buscarFinalizadosPagosPorFinalizacaoEntre(inicio, fim)) {
            LocalDate d = s.getDataFinalizacao();
            if (d == null)
                continue;
            long diff = ChronoUnit.DAYS.between(inicio, d);
            if (diff < 0)
                continue;
            int idx = (int) (diff / tamBucketDias);
            if (idx < 0 || idx >= numBuckets)
                continue;
            BigDecimal v = s.getValor() == null ? BigDecimal.ZERO : s.getValor();
            valores[idx] = valores[idx].add(v);
            qtds[idx] = qtds[idx] + 1L;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<FaturamentoServicoPeriodoDTO> out = new ArrayList<>();
        for (int i = 0; i < numBuckets; i++) {
            out.add(new FaturamentoServicoPeriodoDTO(bucketInicios.get(i).format(fmt), valores[i], qtds[i]));
        }
        return out;
    }

    private List<Servico> buscarFinalizadosPagosPorFinalizacaoEntre(LocalDate inicio, LocalDate fim) {
        return daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataFinalizacao", Condicao.BETWEEN, inicio, fim)
                .list();
    }

}
