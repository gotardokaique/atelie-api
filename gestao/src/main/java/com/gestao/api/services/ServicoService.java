package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.DashboardStatsDTO;
import com.gestao.api.controllers.DTOs.HorarioPicoDTO;
import com.gestao.api.controllers.DTOs.NomeValorDTO;
import com.gestao.api.controllers.DTOs.PessoaRankingDTO;
import com.gestao.api.controllers.DTOs.ResumoFinanceiroDTO;
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.db.Condicao;
import com.gestao.api.db.DAOController;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.exceptions.BusinessException;
import com.gestao.api.services.exceptions.NotFoundException;

@Service
public class ServicoService {

    private static final Logger log = LoggerFactory.getLogger(ServicoService.class);
    private static final StatusServico FINALIZADO = StatusServico.FINALIZADO;

    private final DAOController daoController;
    private final Clock clock;

    public ServicoService(DAOController daoController, Clock clock) {
        this.daoController = daoController;
        this.clock = clock;
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

        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(UserContext.getIdUsuario());
        servico.setUsuario(usuarioRef);

        // Rondônia
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
    }

    // ===================== LISTAR =====================

    @Transactional(readOnly = true)
    public List<ServicoResponseDTO> listarServicosEmAberto(Long pessoaId) {

        List<Servico> servicos;
        try {
            // Observação: seu if/else era igual. Mantive simples.
            servicos = daoController
                    .select()
                    .from(Servico.class)
                    .leftJoin("pessoa")
                    .join("usuario")
                    .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                    .where("statusServico", Condicao.IN,
                            StatusServico.PENDENTE,
                            StatusServico.EM_ANDAMENTO,
                            StatusServico.URGENTE)
                    .orderBy("dataCadastro", false)
                    .list();

        } catch (NotFoundException not) {
            servicos = new ArrayList<>();
        }

        return ServicoResponseDTO.refactor(servicos);
    }

    @Transactional(readOnly = true)
    public List<ServicoResponseDTO> listarServicosFinalizados() {
        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
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

    // ===================== ATUALIZAR STATUS SERVIÇO =====================

    @Transactional
    public void atualizarStatusServico(Long id, StatusServico novoStatus) {
        Servico servico = buscarServicoById(id);

        servico.setStatusServico(novoStatus);

        if (FINALIZADO.equals(servico.getStatusServico())) {
            // Rondônia
            servico.setDataFinalizacao(LocalDate.now(clock));
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

        if (!FINALIZADO.equals(servicoExistente.getStatusServico())) {
            if (servicoExistente.isUrgente()) {
                servicoExistente.setStatusServico(StatusServico.URGENTE);
            } else {
                servicoExistente.setStatusServico(StatusServico.PENDENTE);
            }
        }

        // Rondônia
        LocalDate hoje = LocalDate.now(clock);

        if (servicoExistente.getDataEntregaPrevista() != null
                && servicoExistente.getDataEntregaPrevista().isBefore(hoje)) {
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
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);

        LocalDate dataInicio = mesAtual.atDay(1);
        LocalDate dataFim = hoje; // mês atual ATÉ HOJE

        List<Servico> servicos = buscarServicosFinalizadosEntre(dataInicio, dataFim);
        return somarValor(servicos);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularSomaFinalizadosUltimos7Dias() {
        LocalDate hoje = LocalDate.now(clock);

        LocalDate dataInicio = hoje.minusDays(6); // 7 dias incluindo hoje
        LocalDate dataFim = hoje;

        List<Servico> servicos = buscarServicosFinalizadosEntre(dataInicio, dataFim);
        return somarValor(servicos);
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats() {
        long pendenteCount = countByStatus(StatusServico.PENDENTE);
        long emAndamentoCount = countByStatus(StatusServico.EM_ANDAMENTO);
        long urgenteCount = countByStatus(StatusServico.URGENTE);

        // Rondônia
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = hoje.minusDays(6);
        LocalDate fim = hoje;

        long finalizadosUltimos7DiasCount = countFinalizadosEntre(inicio, fim);

        return new DashboardStatsDTO(
                pendenteCount,
                emAndamentoCount,
                urgenteCount,
                finalizadosUltimos7DiasCount
        );
    }

    @Transactional(readOnly = true)
    public ResumoFinanceiroDTO getResumoFinanceiroMesAtual() {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mesAtual = YearMonth.from(hoje);

        LocalDateTime inicio = mesAtual.atDay(1).atStartOfDay();

        List<Servico> servicos = buscarServicosPagosFinalizadosPorDataCadastroEntre(inicio);

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

        // Se dataCadastro for LocalDateTime (parece ser), isso está OK.
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

    // ===================== SALVAR (INSERT / UPDATE) =====================

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
                .where("dataCadastro", Condicao.GREATER_OR_EQUAL, inicio)
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
    		if (ser.getPessoa() != null ) {
    			nome = ser.getPessoa().getNome();
    		}

    		BigDecimal valor = ser.getValor();

    		retorno.add(new NomeValorDTO(nome, valor));
    	}

    	return retorno;
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
            String nome = p.getNome() ;

            BigDecimal valor = s.getValor();

            PessoaRankingDTO dto = acumulado.get(pessoaId);
            if (dto == null) {
                dto = new PessoaRankingDTO(pessoaId, nome, 0L, BigDecimal.ZERO);
                acumulado.put(pessoaId, dto);
            }

            dto.setQuantidade(dto.getQuantidade() + 1);
            dto.setTotal(dto.getTotal().add(valor));
        }

        // Map -> List
        List<PessoaRankingDTO> ranking = new ArrayList<>();
        for (PessoaRankingDTO dto : acumulado.values()) {
            ranking.add(dto);
        }

        // Ordenação: quantidade desc, depois total desc, depois nome asc
        Collections.sort(ranking, (a, b) -> {
            int c1 = Long.compare(b.getQuantidade(), a.getQuantidade());
            if (c1 != 0) return c1;

            int c2 = b.getTotal().compareTo(a.getTotal());
            if (c2 != 0) return c2;

            return a.getNome().compareToIgnoreCase(b.getNome());
        });

        return ranking;
    }


}
