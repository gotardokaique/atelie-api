package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.controllers.DTOs.DashboardStatsDTO;
import com.gestao.api.controllers.DTOs.HorarioPicoDTO;
import com.gestao.api.controllers.DTOs.ResumoFinanceiroDTO;
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.db.Condicao;
import com.gestao.api.db.DAOController;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.exceptions.BusinessException;
import com.gestao.api.services.exceptions.NotFoundException;

@Service
public class ServicoService {

    private final DAOController daoController;
    private static final Logger log = LoggerFactory.getLogger(ServicoService.class);

    private static final StatusServico FINALIZADO = StatusServico.FINALIZADO;

    public ServicoService(DAOController daoController) {
        this.daoController = daoController;
    }

    // ===================== CRIAR =====================

    @Transactional
    public void criarServico(ServicoRequestDTO requestDTO) {
        Pessoa pessoa = null;

        if (requestDTO.pessoaId() != null) {
            pessoa = buscarPessoaById(requestDTO.pessoaId());
        }

        Servico servico = new Servico();
        servico.setPessoa(pessoa);
        servico.setDescricao(requestDTO.descricao());
        servico.setDataEntregaPrevista(requestDTO.dataEntregaPrevista());
        servico.setValor(requestDTO.valor());
        servico.setUrgente(Boolean.TRUE.equals(requestDTO.urgente()));

        if (servico.getDataEntregaPrevista() != null
                && servico.getDataEntregaPrevista().isBefore(LocalDate.now())) {
            throw new BusinessException("A data de previsão de entrega não pode ser no passado.");
        }

        if (servico.isUrgente()) {
            servico.setStatusServico(StatusServico.URGENTE);
        } else {
            servico.setStatusServico(StatusServico.PENDENTE);
        }

        servico.setStatusPagamento(StatusPagamento.PENDENTE);

        salvar(servico);
    }

    // ===================== LISTAR =====================

    @Transactional(readOnly = true)
    public List<ServicoResponseDTO> listarServicosEmAberto() {

    	List<Servico> servicos;
    	try {
    		servicos = daoController
    				.select()
    				.from(Servico.class)
    				.join("pessoa")
    				.where("statusServico", Condicao.IN,
    						StatusServico.PENDENTE,
    						StatusServico.EM_ANDAMENTO,
    						StatusServico.URGENTE)
    				.orderBy("dataCadastro", false)
    				.list();
    		
    	} catch (NotFoundException not) {
    		servicos= new ArrayList<Servico>();
    	}

        return ServicoResponseDTO.refactor(servicos);
    }

    @Transactional(readOnly = true)
    public List<ServicoResponseDTO> listarServicosFinalizados() {
        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("pessoa")
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .orderBy("dataCadastro", false)
                .list();

        return ServicoResponseDTO.refactor(servicos);
    }

    // ===================== BUSCAR POR ID =====================

    @Transactional(readOnly = true)
    public ServicoResponseDTO buscarServicoPorId(Long id) {
        Servico servico;
        try {
            servico = daoController
                    .select()
                    .from(Servico.class)
                    .join("pessoa")
                    .id(id);

            return ServicoResponseDTO.refactor(servico);
        } catch (NotFoundException e) {
            servico = new Servico();
            return ServicoResponseDTO.refactor(servico);
        }
    }

    // ===================== ATUALIZAR STATUS SERVIÇO =====================

    @Transactional
    public void atualizarStatusServico(Long id, StatusServico novoStatus) {
        Servico servico = buscarServicoById(id);

        servico.setStatusServico(novoStatus);
        if (servico.getStatusServico().equals(FINALIZADO)) {
            servico.setDataFinalizacao(LocalDate.now());
        }

        salvar(servico);
    }

    // ===================== ATUALIZAR STATUS PAGAMENTO =====================

    @Transactional
    public void atualizarStatusPagamento(Long id, StatusPagamento novoStatus) {
        Servico servico = buscarServicoById(id);

        servico.setStatusPagamento(novoStatus);

        salvar(servico);
    }

    // ===================== ATUALIZAR SERVIÇO COMPLETO =====================

    @Transactional
    public void atualizarServicoCompleto(Long id, ServicoRequestDTO requestDTO) {
        Servico servicoExistente = buscarServicoById(id);

        Pessoa pessoa = null;
        if (requestDTO.pessoaId() != null) {
            pessoa = buscarPessoaById(requestDTO.pessoaId());
        }

        servicoExistente.setPessoa(pessoa);
        servicoExistente.setDescricao(requestDTO.descricao());
        servicoExistente.setDataEntregaPrevista(requestDTO.dataEntregaPrevista());
        servicoExistente.setValor(requestDTO.valor());
        servicoExistente.setUrgente(Boolean.TRUE.equals(requestDTO.urgente()));

        if (!servicoExistente.getStatusServico().equals(FINALIZADO)) {
            if (servicoExistente.isUrgente()) {
                servicoExistente.setStatusServico(StatusServico.URGENTE);
            } else {
                servicoExistente.setStatusServico(StatusServico.PENDENTE);
            }
        }

        if (servicoExistente.getDataEntregaPrevista() != null
                && servicoExistente.getDataEntregaPrevista().isBefore(LocalDate.now())) {
            throw new BusinessException("A data de previsão de entrega não pode ser no passado.");
        }

        salvar(servicoExistente);
    }

    // ===================== DELETAR =====================

    @Transactional
    public void deletarServico(Long id) {
        Servico servicoExistente = buscarServicoById(id);
        daoController.delete(servicoExistente);
    }

    // ===================== DASHBOARD / RESUMOS =====================

    @Transactional(readOnly = true)
    public BigDecimal calcularSomaFinalizadosMesAtual() {
        YearMonth mesAtual = YearMonth.now();
        LocalDate dataInicio = mesAtual.atDay(1);
        LocalDate dataFim = mesAtual.atEndOfMonth();

        List<Servico> servicos = buscarServicosFinalizadosEntre(dataInicio, dataFim);
        return somarValor(servicos);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularSomaFinalizadosSemanaAtual() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        List<Servico> servicos = buscarServicosFinalizadosEntre(inicioSemana, fimSemana);
        return somarValor(servicos);
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats() {

        long pendenteCount = countByStatus(StatusServico.PENDENTE);
        long emAndamentoCount = countByStatus(StatusServico.EM_ANDAMENTO);
        long urgenteCount = countByStatus(StatusServico.URGENTE);

        LocalDate hoje = LocalDate.now();
        LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        long finalizadosSemanaCount = countFinalizadosEntre(inicioSemana, fimSemana);

        return new DashboardStatsDTO(
                pendenteCount,
                emAndamentoCount,
                urgenteCount,
                finalizadosSemanaCount
        );
    }

    @Transactional(readOnly = true)
    public ResumoFinanceiroDTO getResumoFinanceiroMesAtual() {
        YearMonth mesAtual = YearMonth.now();
        LocalDate dataInicio = mesAtual.atDay(1);
        LocalDate dataFim = mesAtual.atEndOfMonth();

        List<Servico> servicos = buscarServicosFinalizadosEntre(dataInicio, dataFim);

        BigDecimal soma = somarValor(servicos);
        long contagem = servicos.size();

        return new ResumoFinanceiroDTO(soma, contagem);
    }

    @Transactional(readOnly = true)
    public ResumoFinanceiroDTO getResumoFinanceiroSemanaAtual() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        List<Servico> servicos = buscarServicosFinalizadosEntre(inicioSemana, fimSemana);

        BigDecimal soma = somarValor(servicos);
        long contagem = servicos.size();

        return new ResumoFinanceiroDTO(soma, contagem);
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
                .where("dataCadastro", Condicao.BETWEEN, dataInicioMes, dataFimMesMaisUmDia)
                .list();

        var contagemPorHora = servicos.stream()
                .filter(s -> s.getDataCadastro() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getDataCadastro().getHour(),
                        Collectors.counting()
                ));

        return contagemPorHora.entrySet().stream()
                .map(e -> new HorarioPicoDTO(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(HorarioPicoDTO::hora))
                .toList();
    }

    // ===================== HELPERS PRIVADOS =====================

    private Servico buscarServicoById(Long id) {
        try {
            return daoController
                    .select()
                    .from(Servico.class)
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
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("dataFinalizacao", Condicao.BETWEEN, inicio, fim)
                .list();
    }

    private long countByStatus(StatusServico status) {
        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
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

    // ===================== SALVAR (INSERT / UPDATE) =====================

    @Transactional
    public Servico salvar(Servico servico) {
        if (servico.getId() != null) {
            return daoController.update(servico);
        } else {
            return daoController.insert(servico);
        }
    }
}
