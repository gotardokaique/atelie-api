package com.gestao.api.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gestao.api.controllers.DTOs.HorarioPicoDTO;
import com.gestao.api.controllers.DTOs.NomeValorDTO;
import com.gestao.api.controllers.DTOs.PessoaRankingDTO;
import com.gestao.api.controllers.DTOs.ResumoFinanceiroDTO;
import com.gestao.api.controllers.DTOs.ResumoPendenciasDTO;
import com.gestao.api.services.ServicoService;

@RestController
@RequestMapping("/api/v1/financeiro")
public class FinanceiroController extends AbstractController {

    private final ServicoService servicoService;

    public FinanceiroController(ServicoService servicoService) {
        this.servicoService = servicoService;
    }

    @GetMapping("/resumo-mes-atual")
    public ResponseEntity<ResumoFinanceiroDTO> getResumoMes(@RequestParam(required = false) String mesAno) {
        return ResponseEntity.ok(servicoService.getResumoFinanceiroMesAtual(mesAno));
    }

    @GetMapping("/resumo-semana-atual")
    public ResponseEntity<ResumoFinanceiroDTO> getResumoSemana() {
        return ResponseEntity.ok(servicoService.getResumoFinanceiroUltimos7Dias());
    }

    @GetMapping("/horarios-pico")
    public ResponseEntity<List<HorarioPicoDTO>> getHorariosPico(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes) {

        LocalDate now = LocalDate.now();
        int anoFinal = (ano != null) ? ano : now.getYear();
        int mesFinal = (mes != null) ? mes : now.getMonthValue();

        return ResponseEntity.ok(servicoService.getHorariosDePicoMes(anoFinal, mesFinal));
    }
    
    @GetMapping("/lista-nome-valor-mes-atual")
    public ResponseEntity<List<NomeValorDTO>> listarNomeValorMesAtual(@RequestParam(required = false) String mesAno) {
        return ResponseEntity.ok(servicoService.listarNomeValorMesAtual(mesAno));
    }
    
    @GetMapping("/lista-nome-valor-pendencias-mes-atual")
    public ResponseEntity<List<NomeValorDTO>> listarNomeValorPendenciasMesAtual(@RequestParam(required = false) String mesAno) {
        return ResponseEntity.ok(servicoService.listarNomeValorPendenciasMes(mesAno));
    }
    
    @GetMapping("/pendencias-mes-atual")
    public ResponseEntity<ResumoPendenciasDTO> pendenciasMesAtual(@RequestParam(required = false) String mesAno) {
        return ResponseEntity.ok(servicoService.getResumoPendenciasMesAtual(mesAno));
    }

    @GetMapping("/ranking-pessoas-ultimos-3-meses")
    public ResponseEntity<List<PessoaRankingDTO>> rankingPessoasUltimos3Meses() {
        return ResponseEntity.ok(servicoService.rankPessoasUltimos3Meses());
    }
}
