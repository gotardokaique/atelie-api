package com.gestao.api.controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestao.api.controllers.DTOs.FiltroRelatorioDTO;
import com.gestao.api.services.RelatorioService;

@RestController
@RequestMapping("/api/v1/relatorios")
public class RelatorioController {

    private static final String CSV_MEDIA_TYPE = "text/csv; charset=UTF-8";
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    // =========================================================
    // Ordens de Serviço
    // =========================================================

    @PostMapping("/os/periodo")
    public ResponseEntity<byte[]> osPorPeriodo(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioOSPorPeriodo(filtro);
        return respond("os-por-periodo", filtro, bytes);
    }

    @PostMapping("/os/em-aberto")
    public ResponseEntity<byte[]> osEmAberto(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioOSEmAberto(filtro);
        return respond("os-em-aberto", filtro, bytes);
    }

    @PostMapping("/os/atrasadas")
    public ResponseEntity<byte[]> osAtrasadas(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioOSAtrasadas(filtro);
        return respond("os-atrasadas", filtro, bytes);
    }

    @PostMapping("/os/por-status")
    public ResponseEntity<byte[]> osPorStatus(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioOSPorStatus(filtro);
        return respond("os-por-status", filtro, bytes);
    }

    // =========================================================
    // Clientes
    // =========================================================

    @PostMapping("/clientes/faturamento")
    public ResponseEntity<byte[]> clientesPorFaturamento(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioClientesPorFaturamento(filtro);
        return respond("clientes-faturamento", filtro, bytes);
    }

    @PostMapping("/clientes/inativos")
    public ResponseEntity<byte[]> clientesInativos(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioClientesInativos(filtro);
        return respond("clientes-inativos", filtro, bytes);
    }

    // =========================================================
    // Financeiro
    // =========================================================

    @PostMapping("/financeiro/faturamento")
    public ResponseEntity<byte[]> faturamentoPorPeriodo(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioFaturamentoPorPeriodo(filtro);
        return respond("faturamento-por-periodo", filtro, bytes);
    }

    @PostMapping("/financeiro/comparativo-mensal")
    public ResponseEntity<byte[]> comparativoMensal(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioComparativoMensal(filtro);
        return respond("comparativo-mensal", filtro, bytes);
    }

    @PostMapping("/financeiro/ticket-medio")
    public ResponseEntity<byte[]> ticketMedio(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioTicketMedio(filtro);
        return respond("ticket-medio", filtro, bytes);
    }

    // =========================================================
    // Produção
    // =========================================================

    @PostMapping("/producao/por-etapa")
    public ResponseEntity<byte[]> producaoPorEtapa(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioProducaoPorEtapa(filtro);
        return respond("producao-por-etapa", filtro, bytes);
    }

    @PostMapping("/producao/taxa-prazo")
    public ResponseEntity<byte[]> taxaEntregaNoPrazo(@RequestBody FiltroRelatorioDTO filtro) {
        byte[] bytes = relatorioService.gerarRelatorioTaxaEntregaNoPrazo(filtro);
        return respond("taxa-entrega-no-prazo", filtro, bytes);
    }

    // =========================================================
    // Helpers de resposta
    // =========================================================

    private ResponseEntity<byte[]> respond(String nomeBase, FiltroRelatorioDTO filtro, byte[] bytes) {
        String formato = filtro != null && filtro.formato() != null
                ? filtro.formato().toUpperCase()
                : RelatorioService.FORMATO_PDF;

        String extensao;
        MediaType mediaType;
        switch (formato) {
            case RelatorioService.FORMATO_CSV -> {
                extensao = "csv";
                mediaType = MediaType.parseMediaType(CSV_MEDIA_TYPE);
            }
            case RelatorioService.FORMATO_XLSX -> {
                extensao = "xlsx";
                mediaType = MediaType.parseMediaType(XLSX_MEDIA_TYPE);
            }
            default -> {
                extensao = "pdf";
                mediaType = MediaType.APPLICATION_PDF;
            }
        }

        String filename = nomeBase + "." + extensao;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(bytes);
    }
}
