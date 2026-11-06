package com.gestao.api.controllers;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gestao.api.controllers.DTOs.MeiResumoDTO;
import com.gestao.api.relatorio.mei.RelatorioGerado;
import com.gestao.api.services.MeiService;
import com.gestao.api.services.RelatorioReceitasBrutasService;

@RestController
@RequestMapping("/api/v1/mei")
public class MeiController extends AbstractController {

    private final MeiService meiService;
    private final RelatorioReceitasBrutasService relatorioReceitasBrutasService;

    public MeiController(MeiService meiService, RelatorioReceitasBrutasService relatorioReceitasBrutasService) {
        this.meiService = meiService;
        this.relatorioReceitasBrutasService = relatorioReceitasBrutasService;
    }

    @GetMapping("/resumo")
    public ResponseEntity<MeiResumoDTO> resumo(@RequestParam(required = false) Integer ano) {
        return ResponseEntity.ok(meiService.obterResumo(ano));
    }

    @GetMapping("/relatorio-receitas-brutas/mensal")
    public ResponseEntity<byte[]> relatorioMensal(@RequestParam int ano, @RequestParam int mes) {
        return responderPdf(relatorioReceitasBrutasService.gerarMensal(ano, mes));
    }

    @GetMapping("/relatorio-receitas-brutas/anual")
    public ResponseEntity<byte[]> relatorioAnual(@RequestParam int ano) {
        return responderPdf(relatorioReceitasBrutasService.gerarAnual(ano));
    }

    private ResponseEntity<byte[]> responderPdf(RelatorioGerado relatorio) {
        ContentDisposition disposicao = ContentDisposition.attachment().filename(relatorio.nomeArquivo()).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(relatorio.conteudo());
    }
}
