package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.controllers.DTOs.DashboardStatsDTO;
import com.gestao.api.controllers.DTOs.HorarioPicoDTO;
import com.gestao.api.controllers.DTOs.ResumoFinanceiroDTO;
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.mappers.ServicoMapper;
import com.gestao.api.repositories.PessoaRepository;
import com.gestao.api.repositories.ServicoRepository;
import com.gestao.api.services.exceptions.BusinessException;

@Service
public class ServicoService {

	private final ServicoRepository servicoRepository;
	private final PessoaRepository pessoaRepository;
	private final ServicoMapper servicoMapper;
	private static final Logger log = LoggerFactory.getLogger(ServicoService.class);

	private static final StatusServico finalizado = StatusServico.FINALIZADO;

	public ServicoService(ServicoRepository servicoRepository, PessoaRepository pessoaRepository,
			ServicoMapper servicoMapper) {
		this.servicoRepository = servicoRepository;
		this.pessoaRepository = pessoaRepository;
		this.servicoMapper = servicoMapper;
	}

	@Transactional
	public ServicoResponseDTO criarServico(ServicoRequestDTO requestDTO) {
		Pessoa pessoa = null;
		if (requestDTO.pessoaId() != null) {
			pessoa = pessoaRepository.findById(requestDTO.pessoaId())
					.orElseThrow(() -> new RuntimeException("Pessoa não encontrada com id: " + requestDTO.pessoaId()));
		}

		Servico servico = servicoMapper.toEntity(requestDTO);
		servico.setPessoa(pessoa);
		servico.setUrgente(requestDTO.urgente() != null && requestDTO.urgente());

		if (servico.isUrgente()) {
			servico.setStatusServico(StatusServico.URGENTE);
		} else {
			servico.setStatusServico(StatusServico.PENDENTE);
		}

		if (servico.getDataEntregaPrevista() != null && servico.getDataEntregaPrevista().isBefore(LocalDate.now())) {
			throw new BusinessException("A data de previsão de entrega não pode ser no passado.");
		}
		servico.setStatusPagamento(StatusPagamento.PENDENTE);

		Servico servicoSalvo = servicoRepository.save(servico);
		return servicoMapper.toResponseDto(servicoSalvo);
	}

	 @Transactional(readOnly = true)
	    public List<ServicoResponseDTO> listarServicosEmAberto() {
	        List<StatusServico> openStatuses = Arrays.asList( 
	            StatusServico.PENDENTE,
	            StatusServico.EM_ANDAMENTO,
	            StatusServico.URGENTE
	        );
	        List<Servico> servicos = servicoRepository.findOpenServicesWithPriority(openStatuses);
	        return servicoMapper.toResponseDtoList(servicos);
	    }

	@Transactional(readOnly = true)
	public List<ServicoResponseDTO> listarServicosFinalizados() {
		List<Servico> servicos = servicoRepository.findByStatusServicoOrderByDataCadastroDesc(StatusServico.FINALIZADO);
		return servicoMapper.toResponseDtoList(servicos);
	}

	@Transactional(readOnly = true)
	public Optional<ServicoResponseDTO> buscarServicoPorId(Long id) {
		return servicoRepository.findById(id).map(servicoMapper::toResponseDto);
	}

	@Transactional
	public ServicoResponseDTO atualizarStatusServico(Long id, StatusServico novoStatus) throws Exception {
		Servico servico = servicoRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Serviço não encontrado com id: " + id));

		servico.setStatusServico(novoStatus);
		if (servico.getStatusServico().equals(finalizado)) {
			servico.setDataFinalizacao(LocalDate.now());
		}

		Servico servicoAtualizado = servicoRepository.save(servico);
		System.out.println(servico);

		return servicoMapper.toResponseDto(servicoAtualizado);
	}

	@Transactional
	public ServicoResponseDTO atualizarStatusPagamento(Long id, StatusPagamento novoStatus) {
		Servico servico = servicoRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Serviço não encontrado com id: " + id));
		servico.setStatusPagamento(novoStatus);
		Servico servicoAtualizado = servicoRepository.save(servico);
		return servicoMapper.toResponseDto(servicoAtualizado);

	}

	@Transactional
	public ServicoResponseDTO atualizarServicoCompleto(Long id, ServicoRequestDTO requestDTO) {
		Servico servicoExistente = servicoRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Serviço não encontrado com id: " + id));

		Pessoa pessoa = null;
		if (requestDTO.pessoaId() != null) {
			pessoa = pessoaRepository.findById(requestDTO.pessoaId())
					.orElseThrow(() -> new RuntimeException("Pessoa não encontrada com id: " + requestDTO.pessoaId()));
		}

		servicoExistente.setPessoa(pessoa);
		servicoExistente.setDescricao(requestDTO.descricao());
		servicoExistente.setDataEntregaPrevista(requestDTO.dataEntregaPrevista());
		servicoExistente.setValor(requestDTO.valor());
		servicoExistente.setUrgente(requestDTO.urgente() != null && requestDTO.urgente());

		if (servicoExistente.getStatusServico().equals(finalizado)) {

		} else {
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

		Servico servicoAtualizado = servicoRepository.save(servicoExistente);
		return servicoMapper.toResponseDto(servicoAtualizado);
	}

	@Transactional
	public void deletarServico(Long id) {
		if (!servicoRepository.existsById(id)) {
			throw new RuntimeException("Serviço não encontrado com id: " + id);
		}
		servicoRepository.deleteById(id);
	}

	@Transactional(readOnly = true)
	public BigDecimal calcularSomaFinalizadosMesAtual() {
		YearMonth mesAtual = YearMonth.now();
		LocalDate dataInicio = mesAtual.atDay(1);
		LocalDate dataFim = mesAtual.atEndOfMonth();

		return servicoRepository.sumValorByStatusAndDataFinalizacaoBetween(StatusServico.FINALIZADO, dataInicio,
				dataFim);
	}

	@Transactional(readOnly = true)
	public BigDecimal calcularSomaFinalizadosSemanaAtual() {
		LocalDate hoje = LocalDate.now();
		LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

		return servicoRepository.sumValorByStatusAndDataFinalizacaoBetween(StatusServico.FINALIZADO, inicioSemana,
				fimSemana);
	}

	@Transactional(readOnly = true)
	public DashboardStatsDTO getDashboardStats() {
		long pendenteCount = servicoRepository.countByStatusServico(StatusServico.PENDENTE);
		long emAndamentoCount = servicoRepository.countByStatusServico(StatusServico.EM_ANDAMENTO);
		long urgenteCount = servicoRepository.countByStatusServico(StatusServico.URGENTE);

		LocalDate hoje = LocalDate.now();
		LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

		long finalizadosSemanaCount = servicoRepository
				.countByStatusServicoAndDataFinalizacaoBetween(StatusServico.FINALIZADO, inicioSemana, fimSemana);

		return new DashboardStatsDTO(pendenteCount, emAndamentoCount, urgenteCount, finalizadosSemanaCount);
	}

	@Transactional(readOnly = true)
	public ResumoFinanceiroDTO getResumoFinanceiroMesAtual() {
		YearMonth mesAtual = YearMonth.now();
		LocalDate dataInicio = mesAtual.atDay(1);
		LocalDate dataFim = mesAtual.atEndOfMonth();
		StatusServico status = StatusServico.FINALIZADO;

		BigDecimal soma = servicoRepository.sumValorByStatusAndDataFinalizacaoBetween(status, dataInicio, dataFim);
		long contagem = servicoRepository.countByStatusServicoAndDataFinalizacaoBetween(status, dataInicio, dataFim);

		return new ResumoFinanceiroDTO(soma, contagem);
	}

	@Transactional(readOnly = true)
	public ResumoFinanceiroDTO getResumoFinanceiroSemanaAtual() {
		LocalDate hoje = LocalDate.now();
		LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate fimSemana = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
		StatusServico status = StatusServico.FINALIZADO;

		BigDecimal soma = servicoRepository.sumValorByStatusAndDataFinalizacaoBetween(status, inicioSemana, fimSemana);
		long contagem = servicoRepository.countByStatusServicoAndDataFinalizacaoBetween(status, inicioSemana,
				fimSemana);

		return new ResumoFinanceiroDTO(soma, contagem);
	}
	
    @Transactional(readOnly = true)
    public List<HorarioPicoDTO> getHorariosDePicoMes(int ano, int mes) {
        YearMonth anoMes = YearMonth.of(ano, mes);
        LocalDateTime dataInicioMes = anoMes.atDay(1).atStartOfDay(); 
        LocalDateTime dataFimMesMaisUmDia = anoMes.plusMonths(1).atDay(1).atStartOfDay(); 

        log.info("Buscando horários de pico para o mês: {}/{} ({} a {})", mes, ano, dataInicioMes, dataFimMesMaisUmDia);

        return servicoRepository.findHorariosDePicoPorMes(dataInicioMes, dataFimMesMaisUmDia);
    }

}