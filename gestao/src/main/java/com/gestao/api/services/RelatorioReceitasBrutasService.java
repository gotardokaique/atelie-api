package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.security.exception.BusinessException;
import com.gen.core.security.exception.InfoException;
import com.gestao.api.context.UserContext;
import com.gestao.api.entities.Atelie;
import com.gestao.api.relatorio.mei.EmitenteRelatorio;
import com.gestao.api.relatorio.mei.LogoEmCinza;
import com.gestao.api.relatorio.mei.ReceitaBrutaMensal;
import com.gestao.api.relatorio.mei.RelatorioGerado;
import com.gestao.api.relatorio.mei.RelatorioReceitasBrutasPdf;

@Service
public class RelatorioReceitasBrutasService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final String PREFIXO_ARQUIVO = "relatorio-receitas-brutas-";

    private final DAOController daoController;
    private final MeiService meiService;
    private final Clock clock;

    public RelatorioReceitasBrutasService(DAOController daoController, MeiService meiService, Clock clock) {
        this.daoController = daoController;
        this.meiService = meiService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RelatorioGerado gerarMensal(int ano, int mes) {
        if (mes < 1 || mes > 12) {
            throw new BusinessException("Mês inválido.");
        }
        YearMonth competencia = YearMonth.of(ano, mes);
        YearMonth mesAtual = YearMonth.now(clock);
        if (competencia.isAfter(mesAtual)) {
            throw new BusinessException("Esse mês ainda não começou. Escolha um mês até " + formatarMesAno(mesAtual) + ".");
        }

        EmitenteRelatorio emitente = montarEmitente();
        BigDecimal[] receitas = meiService.receitaServicosPorMes(ano);
        List<ReceitaBrutaMensal> meses = List.of(montarMes(competencia, receitas, mesAtual));

        byte[] pdf = RelatorioReceitasBrutasPdf.gerar(emitente, meses, LocalDateTime.now(clock));
        return new RelatorioGerado(pdf, PREFIXO_ARQUIVO + competencia + ".pdf");
    }

    @Transactional(readOnly = true)
    public RelatorioGerado gerarAnual(int ano) {
        YearMonth mesAtual = YearMonth.now(clock);
        if (ano > mesAtual.getYear()) {
            throw new BusinessException("O ano " + ano + " ainda não começou.");
        }

        EmitenteRelatorio emitente = montarEmitente();
        BigDecimal[] receitas = meiService.receitaServicosPorMes(ano);

        int ultimoMes = ano == mesAtual.getYear() ? mesAtual.getMonthValue() : 12;
        List<ReceitaBrutaMensal> meses = new ArrayList<>();
        for (int mes = 1; mes <= ultimoMes; mes++) {
            meses.add(montarMes(YearMonth.of(ano, mes), receitas, mesAtual));
        }

        byte[] pdf = RelatorioReceitasBrutasPdf.gerar(emitente, meses, LocalDateTime.now(clock));
        return new RelatorioGerado(pdf, PREFIXO_ARQUIVO + ano + ".pdf");
    }

    private ReceitaBrutaMensal montarMes(YearMonth competencia, BigDecimal[] receitas, YearMonth mesAtual) {
        boolean parcial = competencia.equals(mesAtual);
        LocalDate apuradoAte = parcial ? LocalDate.now(clock) : competencia.atEndOfMonth();
        BigDecimal servicos = receitas[competencia.getMonthValue() - 1];

        return new ReceitaBrutaMensal(
                competencia,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                servicos,
                ZERO,
                parcial,
                apuradoAte,
                novoIdentificador());
    }

    private EmitenteRelatorio montarEmitente() {
        List<Atelie> atelies = daoController
                .select()
                .from(Atelie.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .limit(1)
                .list();
        Atelie atelie = atelies.isEmpty() ? new Atelie() : atelies.get(0);

        String cnpj = somenteDigitos(atelie.getCnpjCpf());
        String responsavel = atelie.getNomeResponsavel();

        List<String> pendencias = new ArrayList<>();
        if (cnpj.length() != 14) {
            pendencias.add(cnpj.length() == 11 ? "o CNPJ do MEI (hoje está cadastrado um CPF)" : "o CNPJ do MEI");
        }
        if (responsavel == null || responsavel.isBlank()) {
            pendencias.add("o nome do responsável (empreendedor individual)");
        }
        if (pendencias.isEmpty() == false) {
            throw new InfoException("Para gerar o relatório, preencha " + String.join(" e ", pendencias)
                    + " em Configurações > Ateliê.");
        }

        return new EmitenteRelatorio(
                atelie.getNome(),
                formatarCnpj(cnpj),
                responsavel.trim(),
                atelie.getCidade(),
                atelie.getUf(),
                LogoEmCinza.converter(atelie.getLogo()));
    }

    private String somenteDigitos(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replaceAll("\\D", "");
    }

    private String formatarCnpj(String digitos) {
        return digitos.substring(0, 2) + "." + digitos.substring(2, 5) + "." + digitos.substring(5, 8) + "/"
                + digitos.substring(8, 12) + "-" + digitos.substring(12);
    }

    private String formatarMesAno(YearMonth competencia) {
        return String.format("%02d/%d", competencia.getMonthValue(), competencia.getYear());
    }

    private String novoIdentificador() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
