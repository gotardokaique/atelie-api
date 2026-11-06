package com.gestao.api.relatorio.mei;

import static com.gen.core.export.html.HtmlPdfService.escapar;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gen.core.export.html.HtmlPdfService;

public final class RelatorioReceitasBrutasPdf {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_EXTENSO = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR);
    private static final DateTimeFormatter MES_EXTENSO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR);

    private static final String TEMPLATE = "/relatorio/mei/relatorio-receitas-brutas.html";
    private static final Pattern MARCADOR = Pattern.compile("\\{\\{(\\w+)}}");
    private static final String CLASSE_ZERO = "zero";

    private static String template;

    private RelatorioReceitasBrutasPdf() {}

    public static byte[] gerar(EmitenteRelatorio emitente, List<ReceitaBrutaMensal> meses, LocalDateTime geradoEm) {
        if (meses == null || meses.isEmpty()) {
            throw new IllegalArgumentException("Nenhum mês para gerar o relatório.");
        }
        List<byte[]> documentos = new ArrayList<>();
        for (ReceitaBrutaMensal mes : meses) {
            documentos.add(new HtmlPdfService().gerar(html(emitente, mes, geradoEm)));
        }
        return HtmlPdfService.juntar(documentos);
    }

    static String html(EmitenteRelatorio emitente, ReceitaBrutaMensal mes, LocalDateTime geradoEm) {
        return preencher(carregarTemplate(), valores(emitente, mes, geradoEm));
    }

    private static Map<String, String> valores(EmitenteRelatorio emitente, ReceitaBrutaMensal mes, LocalDateTime geradoEm) {
        YearMonth competencia = mes.competencia();
        Map<String, String> v = new HashMap<>();

        v.put("competenciaCurta", String.format("%02d/%d", competencia.getMonthValue(), competencia.getYear()));
        v.put("competenciaExtenso", escapar(capitalizar(competencia.format(MES_EXTENSO))));
        v.put("documento", escapar(mes.identificador()));
        v.put("marca", marca(emitente));
        v.put("selo", mes.parcial()
                ? "<div class=\"selo\">PARCIAL · apurado até " + mes.apuradoAte().format(DATA) + "</div>"
                : "");

        v.put("cnpj", escapar(emitente.cnpjFormatado()));
        v.put("empreendedor", escapar(emitente.nomeResponsavel()));
        v.put("periodo", competencia.atDay(1).format(DATA) + " a " + competencia.atEndOfMonth().format(DATA));
        v.put("totalDestaque", reais(mes.totalGeral()));

        linha(v, "I", mes.comercioComDispensa());
        linha(v, "II", mes.comercioComDocumento());
        linha(v, "III", mes.totalComercio());
        linha(v, "IV", mes.industriaComDispensa());
        linha(v, "V", mes.industriaComDocumento());
        linha(v, "VI", mes.totalIndustria());
        linha(v, "VII", mes.servicosComDispensa());
        linha(v, "VIII", mes.servicosComDocumento());
        linha(v, "IX", mes.totalServicos());
        v.put("valorX", reais(mes.totalGeral()));

        v.put("localData", escapar(localData(emitente, geradoEm.toLocalDate())));
        return v;
    }

    private static void linha(Map<String, String> v, String numeral, BigDecimal valor) {
        v.put("valor" + numeral, reais(valor));
        v.put("classe" + numeral, valor.signum() == 0 ? CLASSE_ZERO : "");
    }

    private static String marca(EmitenteRelatorio emitente) {
        if (emitente.nomeAtelie() == null || emitente.nomeAtelie().isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<table><tr>");
        if (emitente.logoEmCinza() != null) {
            html.append("<td class=\"marca-logo\"><img src=\"data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(emitente.logoEmCinza()))
                    .append("\" alt=\"\" /></td>");
        }
        html.append("<td class=\"marca-nome\">").append(escapar(emitente.nomeAtelie())).append("</td>");
        return html.append("</tr></table>").toString();
    }

    private static String localData(EmitenteRelatorio emitente, LocalDate data) {
        String local = emitente.localidade();
        return (local != null ? local + ", " : "") + data.format(DATA_EXTENSO);
    }

    private static String reais(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + formato.format(valor);
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : texto.substring(0, 1).toUpperCase(PT_BR) + texto.substring(1);
    }

    private static String preencher(String modelo, Map<String, String> valores) {
        Matcher m = MARCADOR.matcher(modelo);
        StringBuilder resultado = new StringBuilder(modelo.length() + 4096);
        while (m.find()) {
            String valor = valores.get(m.group(1));
            if (valor == null) {
                throw new IllegalStateException("Marcador sem valor no template do relatório: " + m.group(1));
            }
            m.appendReplacement(resultado, Matcher.quoteReplacement(valor));
        }
        m.appendTail(resultado);
        return resultado.toString();
    }

    private static synchronized String carregarTemplate() {
        if (template == null) {
            try (InputStream entrada = RelatorioReceitasBrutasPdf.class.getResourceAsStream(TEMPLATE)) {
                if (entrada == null) {
                    throw new IllegalStateException("Template não encontrado: " + TEMPLATE);
                }
                template = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Erro ao ler o template do relatório.", e);
            }
        }
        return template;
    }
}
