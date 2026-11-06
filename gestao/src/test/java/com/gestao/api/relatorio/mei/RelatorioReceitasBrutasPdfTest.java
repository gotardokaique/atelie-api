//package com.gestao.api.relatorio.mei;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import java.io.IOException;
//import java.math.BigDecimal;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.YearMonth;
//import java.util.List;
//
//import javax.imageio.ImageIO;
//
//import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.rendering.PDFRenderer;
//import org.apache.pdfbox.text.PDFTextStripper;
//import org.junit.jupiter.api.Test;
//
//class RelatorioReceitasBrutasPdfTest {
//
//    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
//    private static final LocalDateTime GERADO_EM = LocalDateTime.of(2026, 11, 5, 14, 30);
//
//    private final EmitenteRelatorio emitente = new EmitenteRelatorio(
//            "Ateliê da Maria", "12.345.678/0001-90", "Maria José da Conceição", "São José dos Campos", "SP", null);
//
//    private ReceitaBrutaMensal mes(int mes, String servicos, boolean parcial, String identificador) {
//        YearMonth competencia = YearMonth.of(2026, mes);
//        LocalDate apuradoAte = parcial ? competencia.atDay(5) : competencia.atEndOfMonth();
//        return new ReceitaBrutaMensal(competencia, ZERO, ZERO, ZERO, ZERO, new BigDecimal(servicos), ZERO,
//                parcial, apuradoAte, identificador);
//    }
//
//    private void salvarPrevia(String nome, byte[] pdf) throws IOException {
//        if (Boolean.getBoolean("relatorio.previa") == false) {
//            return;
//        }
//        Path pasta = Path.of("target", "previa-relatorio");
//        Files.createDirectories(pasta);
//        Files.write(pasta.resolve(nome + ".pdf"), pdf);
//        try (PDDocument documento = Loader.loadPDF(pdf)) {
//            PDFRenderer renderizador = new PDFRenderer(documento);
//            Files.writeString(pasta.resolve(nome + ".txt"), textoDaPagina(documento, 1));
//            for (int i = 0; i < documento.getNumberOfPages(); i++) {
//                ImageIO.write(renderizador.renderImageWithDPI(i, 110), "png", pasta.resolve(nome + "-" + (i + 1) + ".png").toFile());
//            }
//        }
//    }
//
//    private String textoDaPagina(PDDocument documento, int pagina) throws IOException {
//        PDFTextStripper extrator = new PDFTextStripper();
//        extrator.setSortByPosition(true);
//        extrator.setStartPage(pagina);
//        extrator.setEndPage(pagina);
//        return extrator.getText(documento);
//    }
//
//    @Test
//    void geraMesEmUmaPaginaComTotaisEAcentuacao() throws IOException {
//        byte[] pdf = RelatorioReceitasBrutasPdf.gerar(emitente, List.of(mes(10, "4532.90", false, "A1B2C3D4")), GERADO_EM);
//        salvarPrevia("mensal", pdf);
//
//        try (PDDocument documento = Loader.loadPDF(pdf)) {
//            String texto = textoDaPagina(documento, 1);
//
//            assertEquals(1, documento.getNumberOfPages());
//            assertTrue(texto.contains("Relatório Mensal das Receitas Brutas"));
//            assertTrue(texto.contains("Ateliê da Maria"));
//            assertTrue(texto.contains("Outubro de 2026"));
//            assertTrue(texto.contains("Documento nº A1B2C3D4"));
//            assertTrue(texto.contains("R$ 4.532,90"));
//            assertTrue(texto.contains("São José dos Campos/SP, 5 de novembro de 2026"));
//            assertTrue(texto.contains("Gerado pelo Gestão Ateliê em 05/11/2026 às 14:30"));
//            assertTrue(texto.contains("Competência 10/2026 · Página 1 de 1"));
//            assertFalse(texto.contains("PARCIAL"));
//        }
//    }
//
//    @Test
//    void semNomeFantasiaNaoMostraMarcaNoTopo() {
//        EmitenteRelatorio semNome = new EmitenteRelatorio(
//                null, "12.345.678/0001-90", "Maria José da Conceição", null, null, null);
//
//        String html = RelatorioReceitasBrutasPdf.html(semNome, mes(10, "100.00", false, "A1B2C3D4"), GERADO_EM);
//
//        assertFalse(html.contains("class=\"marca-nome\""));
//    }
//
//    @Test
//    void anoCompletoTemUmDocumentoIndependentePorMes() throws IOException {
//        byte[] pdf = RelatorioReceitasBrutasPdf.gerar(emitente,
//                List.of(mes(9, "3100.00", false, "AAAA1111"), mes(10, "4532.90", false, "BBBB2222"),
//                        mes(11, "780.50", true, "CCCC3333")),
//                GERADO_EM);
//        salvarPrevia("anual", pdf);
//
//        try (PDDocument documento = Loader.loadPDF(pdf)) {
//            assertEquals(3, documento.getNumberOfPages());
//            assertTrue(textoDaPagina(documento, 1).contains("Documento nº AAAA1111"));
//            assertTrue(textoDaPagina(documento, 1).contains("Competência 09/2026 · Página 1 de 1"));
//            assertTrue(textoDaPagina(documento, 2).contains("Documento nº BBBB2222"));
//            assertTrue(textoDaPagina(documento, 2).contains("Competência 10/2026 · Página 1 de 1"));
//            assertTrue(textoDaPagina(documento, 3).contains("Documento nº CCCC3333"));
//            assertTrue(textoDaPagina(documento, 3).contains("PARCIAL · apurado até 05/11/2026"));
//        }
//    }
//}
