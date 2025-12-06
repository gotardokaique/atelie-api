package com.gestao.api.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gestao.api.controllers.DTOs.HorarioPicoDTO;
import com.gestao.api.controllers.DTOs.ResumoFinanceiroDTO;
import com.gestao.api.services.ServicoService;

@RestController
@RequestMapping("/api/v1/financeiro")
public class FinanceiroController {

	private final ServicoService servicoService;

	public FinanceiroController(ServicoService servicoService) {
		this.servicoService = servicoService;
	}

	@GetMapping("/resumo-mes-atual")
	public ResponseEntity<ResumoFinanceiroDTO> getResumoMes() {
		ResumoFinanceiroDTO dto = servicoService.getResumoFinanceiroMesAtual();
		return ResponseEntity.ok(dto);
	}

	@GetMapping("/resumo-semana-atual")
	public ResponseEntity<ResumoFinanceiroDTO> getResumoSemana() {

		ResumoFinanceiroDTO dto = servicoService.getResumoFinanceiroSemanaAtual();
		return ResponseEntity.ok(dto);
	}

	@GetMapping("/horarios-pico")
	public ResponseEntity<List<HorarioPicoDTO>> getHorariosPico(
			@RequestParam(value = "ano", required = false) Integer ano,
			@RequestParam(value = "mes", required = false) Integer mes) {

		if (ano == null) {
			ano = LocalDate.now().getYear();
		}
		if (mes == null) {
			mes = LocalDate.now().getMonthValue();
		}

		List<HorarioPicoDTO> horariosPico = servicoService.getHorariosDePicoMes(ano, mes);
		return ResponseEntity.ok(horariosPico);
	}
}