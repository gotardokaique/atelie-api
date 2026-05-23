package com.gestao.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.export.csv.CsvService;
import com.gen.core.export.excel.WorkService;
import com.gen.core.export.report.ReportService;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.FiltroRelatorioDTO;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;

@Service
public class RelatorioService {

    private static final DateTimeFormatter DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MES_ANO_BR = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Locale BR = new Locale("pt", "BR");

    public static final String FORMATO_PDF = "PDF";
    public static final String FORMATO_CSV = "CSV";
    public static final String FORMATO_XLSX = "XLSX";

    private final DAOController dao;
    private final Clock clock;

    public RelatorioService(DAOController dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    // =========================================================
    // Ordens de Serviço
    // =========================================================

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioOSPorPeriodo(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .leftJoin("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("dataCadastro", Condicao.BETWEEN, intervalo.inicio(), intervalo.fim())
                .orderBy("dataCadastro", false)
                .list();

        TabelaRelatorio tabela = new TabelaRelatorio(
                "Relatório de OS por Período",
                subtituloPeriodo(intervalo),
                new String[]{"Nº OS", "Cliente", "Descrição", "Status", "Data Abertura", "Prazo", "Valor"}
        );

        for (Servico serBean : servicos) {
            tabela.addRow(
            		serBean.getId(),
                    nomePessoa(serBean.getPessoa()),
                    textoOuTraco(serBean.getDescricao()),
                    serBean.getStatusServico() != null ? serBean.getStatusServico().name() : "-",
                    fmt(serBean.getDataCadastro()),
                    fmt(serBean.getDataEntregaPrevista()),
                    moeda(serBean.getValor())
            );
        }

        return renderizar(tabela, filtro);
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioOSEmAberto(FiltroRelatorioDTO filtro) {
        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .leftJoin("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.IN,
                        StatusServico.PENDENTE, StatusServico.EM_ANDAMENTO, StatusServico.URGENTE)
                .orderBy("dataEntregaPrevista", true)
                .list();

        LocalDate hoje = LocalDate.now(clock);
        TabelaRelatorio tabela = new TabelaRelatorio(
                "OS em Aberto",
                "Geração: " + hoje.format(DATE_BR),
                new String[]{"Nº OS", "Cliente", "Dias Restantes", "Status", "Valor"}
        );

        for (Servico s : servicos) {
            String diasRestantes = "-";
            if (s.getDataEntregaPrevista() != null) {
                long dias = ChronoUnit.DAYS.between(hoje, s.getDataEntregaPrevista());
                diasRestantes = String.valueOf(dias);
            }

            tabela.addRow(
                    s.getId(),
                    nomePessoa(s.getPessoa()),
                    diasRestantes,
                    s.getStatusServico() != null ? s.getStatusServico().name() : "-",
                    moeda(s.getValor())
            );
        }

        return renderizar(tabela, filtro);
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioOSAtrasadas(FiltroRelatorioDTO filtro) {
        LocalDate hoje = LocalDate.now(clock);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .leftJoin("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.IN,
                        StatusServico.PENDENTE, StatusServico.EM_ANDAMENTO, StatusServico.URGENTE)
                .where("dataEntregaPrevista", Condicao.LESS_THAN, hoje)
                .orderBy("dataEntregaPrevista", true)
                .list();

        TabelaRelatorio tabela = new TabelaRelatorio(
                "OS Atrasadas",
                "Geração: " + hoje.format(DATE_BR),
                new String[]{"Nº OS", "Cliente", "Dias de Atraso", "Contato do Cliente", "Valor"}
        );

        for (Servico s : servicos) {
            long diasAtraso = s.getDataEntregaPrevista() == null
                    ? 0
                    : ChronoUnit.DAYS.between(s.getDataEntregaPrevista(), hoje);

            String contato = "-";
            if (s.getPessoa() != null && s.getPessoa().getTelefone() != null) {
                contato = s.getPessoa().getTelefone();
            }

            tabela.addRow(
                    s.getId(),
                    nomePessoa(s.getPessoa()),
                    String.valueOf(diasAtraso),
                    contato,
                    moeda(s.getValor())
            );
        }

        return renderizar(tabela, filtro);
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioOSPorStatus(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("dataCadastro", Condicao.BETWEEN, intervalo.inicio(), intervalo.fim())
                .list();

        Map<StatusServico, Long> contagem = new LinkedHashMap<>();
        Map<StatusServico, BigDecimal> totalValor = new LinkedHashMap<>();
        for (StatusServico status : StatusServico.values()) {
            contagem.put(status, 0L);
            totalValor.put(status, BigDecimal.ZERO);
        }

        for (Servico s : servicos) {
            StatusServico st = s.getStatusServico();
            if (st == null) continue;
            contagem.put(st, contagem.get(st) + 1L);
            BigDecimal valor = s.getValor() != null ? s.getValor() : BigDecimal.ZERO;
            totalValor.put(st, totalValor.get(st).add(valor));
        }

        TabelaRelatorio tabela = new TabelaRelatorio(
                "OS por Status",
                subtituloPeriodo(intervalo),
                new String[]{"Status", "Quantidade", "Valor Total"}
        );

        for (StatusServico st : StatusServico.values()) {
            tabela.addRow(st.name(), contagem.get(st), moeda(totalValor.get(st)));
        }

        return renderizar(tabela, filtro);
    }

    // =========================================================
    // Clientes
    // =========================================================

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioClientesPorFaturamento(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.BETWEEN, intervalo.inicio(), intervalo.fim())
                .list();

        Map<Long, AcumuladoCliente> acumulado = new HashMap<>();
        for (Servico s : servicos) {
            Pessoa p = s.getPessoa();
            if (p == null || p.getId() == null) continue;

            AcumuladoCliente acc = acumulado.computeIfAbsent(p.getId(),
                    id -> new AcumuladoCliente(p.getNome()));
            acc.quantidade++;
            BigDecimal valor = s.getValor() != null ? s.getValor() : BigDecimal.ZERO;
            acc.total = acc.total.add(valor);
        }

        List<AcumuladoCliente> ordenado = new ArrayList<>(acumulado.values());
        ordenado.sort((a, b) -> b.total.compareTo(a.total));

        TabelaRelatorio tabela = new TabelaRelatorio(
                "Clientes por Faturamento",
                subtituloPeriodo(intervalo),
                new String[]{"Ranking", "Cliente", "Qtd de OS", "Ticket Médio", "Total Faturado"}
        );

        int ranking = 1;
        for (AcumuladoCliente acc : ordenado) {
            BigDecimal ticket = acc.quantidade == 0
                    ? BigDecimal.ZERO
                    : acc.total.divide(BigDecimal.valueOf(acc.quantidade), 2, RoundingMode.HALF_UP);

            tabela.addRow(ranking++, acc.nome, acc.quantidade, moeda(ticket), moeda(acc.total));
        }

        return renderizar(tabela, filtro);
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioClientesInativos(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);
        LocalDate hoje = LocalDate.now(clock);

        List<Pessoa> pessoas = dao.select()
                .from(Pessoa.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .orderBy("nome", true)
                .list();

        List<Servico> todasOS = dao.select()
                .from(Servico.class)
                .join("pessoa")
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .list();

        Map<Long, LocalDateTime> ultimaOS = new HashMap<>();
        Map<Long, BigDecimal> totalHistorico = new HashMap<>();
        Map<Long, Boolean> teveOSNoPeriodo = new HashMap<>();

        for (Servico s : todasOS) {
            if (s.getPessoa() == null || s.getPessoa().getId() == null) continue;
            Long pid = s.getPessoa().getId();

            BigDecimal valor = s.getValor() != null ? s.getValor() : BigDecimal.ZERO;
            totalHistorico.merge(pid, valor, BigDecimal::add);

            LocalDateTime dc = s.getDataCadastro();
            if (dc != null) {
                LocalDateTime atual = ultimaOS.get(pid);
                if (atual == null || dc.isAfter(atual)) {
                    ultimaOS.put(pid, dc);
                }
                if (!dc.isBefore(intervalo.inicio()) && !dc.isAfter(intervalo.fim())) {
                    teveOSNoPeriodo.put(pid, true);
                }
            }
        }

        TabelaRelatorio tabela = new TabelaRelatorio(
                "Clientes Inativos",
                subtituloPeriodo(intervalo),
                new String[]{"Cliente", "Última OS", "Dias Sem Pedido", "Total Histórico"}
        );

        for (Pessoa p : pessoas) {
            if (Boolean.TRUE.equals(teveOSNoPeriodo.get(p.getId()))) continue;

            LocalDateTime ultima = ultimaOS.get(p.getId());
            String ultimaTxt = ultima == null ? "Nunca" : ultima.toLocalDate().format(DATE_BR);
            String diasSem = ultima == null
                    ? "-"
                    : String.valueOf(ChronoUnit.DAYS.between(ultima.toLocalDate(), hoje));

            BigDecimal total = totalHistorico.getOrDefault(p.getId(), BigDecimal.ZERO);
            tabela.addRow(p.getNome(), ultimaTxt, diasSem, moeda(total));
        }

        return renderizar(tabela, filtro);
    }

    // =========================================================
    // Financeiro
    // =========================================================

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioFaturamentoPorPeriodo(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("dataCadastro", Condicao.BETWEEN, intervalo.inicio(), intervalo.fim())
                .list();

        Map<YearMonth, AcumuladoMes> porMes = new LinkedHashMap<>();
        for (YearMonth ym : iterarMeses(intervalo)) {
            porMes.put(ym, new AcumuladoMes());
        }

        for (Servico s : servicos) {
            if (s.getDataCadastro() == null) continue;
            YearMonth ym = YearMonth.from(s.getDataCadastro());
            AcumuladoMes acc = porMes.computeIfAbsent(ym, k -> new AcumuladoMes());

            acc.quantidade++;
            BigDecimal valor = s.getValor() != null ? s.getValor() : BigDecimal.ZERO;
            acc.prevista = acc.prevista.add(valor);
            if (StatusPagamento.PAGO.equals(s.getStatusPagamento())) {
                acc.realizada = acc.realizada.add(valor);
            }
        }

        TabelaRelatorio tabela = new TabelaRelatorio(
                "Faturamento por Período",
                subtituloPeriodo(intervalo),
                new String[]{"Mês", "Qtd OS", "Receita Realizada", "Receita Prevista"}
        );

        for (Map.Entry<YearMonth, AcumuladoMes> e : porMes.entrySet()) {
            AcumuladoMes acc = e.getValue();
            tabela.addRow(
                    e.getKey().format(MES_ANO_BR),
                    acc.quantidade,
                    moeda(acc.realizada),
                    moeda(acc.prevista)
            );
        }

        return renderizar(tabela, filtro);
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioComparativoMensal(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("dataCadastro", Condicao.BETWEEN, intervalo.inicio(), intervalo.fim())
                .list();

        Map<YearMonth, AcumuladoComparativo> porMes = new LinkedHashMap<>();
        for (YearMonth ym : iterarMeses(intervalo)) {
            porMes.put(ym, new AcumuladoComparativo());
        }

        for (Servico s : servicos) {
            if (s.getDataCadastro() == null) continue;
            YearMonth ym = YearMonth.from(s.getDataCadastro());
            AcumuladoComparativo acc = porMes.computeIfAbsent(ym, k -> new AcumuladoComparativo());

            BigDecimal valor = s.getValor() != null ? s.getValor() : BigDecimal.ZERO;
            if (StatusPagamento.PAGO.equals(s.getStatusPagamento())) {
                acc.faturamento = acc.faturamento.add(valor);
            }
            acc.abertas++;
            if (StatusServico.FINALIZADO.equals(s.getStatusServico())) {
                acc.entregues++;
            }
        }

        TabelaRelatorio tabela = new TabelaRelatorio(
                "Comparativo Mensal",
                subtituloPeriodo(intervalo),
                new String[]{"Mês", "Faturamento", "Variação %", "OSs Abertas", "OSs Entregues"}
        );

        BigDecimal anterior = null;
        for (Map.Entry<YearMonth, AcumuladoComparativo> e : porMes.entrySet()) {
            AcumuladoComparativo acc = e.getValue();
            String variacao = "-";
            if (anterior != null && anterior.signum() != 0) {
                BigDecimal delta = acc.faturamento.subtract(anterior)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(anterior, 2, RoundingMode.HALF_UP);
                variacao = delta.toPlainString() + "%";
            }
            tabela.addRow(
                    e.getKey().format(MES_ANO_BR),
                    moeda(acc.faturamento),
                    variacao,
                    acc.abertas,
                    acc.entregues
            );
            anterior = acc.faturamento;
        }

        return renderizar(tabela, filtro);
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioTicketMedio(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataCadastro", Condicao.BETWEEN, intervalo.inicio(), intervalo.fim())
                .list();

        Map<YearMonth, AcumuladoTicket> porMes = new LinkedHashMap<>();
        for (YearMonth ym : iterarMeses(intervalo)) {
            porMes.put(ym, new AcumuladoTicket());
        }

        for (Servico s : servicos) {
            if (s.getDataCadastro() == null) continue;
            YearMonth ym = YearMonth.from(s.getDataCadastro());
            AcumuladoTicket acc = porMes.computeIfAbsent(ym, k -> new AcumuladoTicket());

            BigDecimal valor = s.getValor() != null ? s.getValor() : BigDecimal.ZERO;
            acc.total = acc.total.add(valor);
            acc.quantidade++;
            if (acc.maior == null || valor.compareTo(acc.maior) > 0) acc.maior = valor;
            if (acc.menor == null || valor.compareTo(acc.menor) < 0) acc.menor = valor;
        }

        TabelaRelatorio tabela = new TabelaRelatorio(
                "Ticket Médio",
                subtituloPeriodo(intervalo),
                new String[]{"Período", "Ticket Médio", "Maior OS", "Menor OS"}
        );

        for (Map.Entry<YearMonth, AcumuladoTicket> e : porMes.entrySet()) {
            AcumuladoTicket acc = e.getValue();
            BigDecimal ticket = acc.quantidade == 0
                    ? BigDecimal.ZERO
                    : acc.total.divide(BigDecimal.valueOf(acc.quantidade), 2, RoundingMode.HALF_UP);
            tabela.addRow(
                    e.getKey().format(MES_ANO_BR),
                    moeda(ticket),
                    moeda(acc.maior == null ? BigDecimal.ZERO : acc.maior),
                    moeda(acc.menor == null ? BigDecimal.ZERO : acc.menor)
            );
        }

        return renderizar(tabela, filtro);
    }

    // =========================================================
    // Produção
    // =========================================================

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioProducaoPorEtapa(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);
        LocalDate hoje = LocalDate.now(clock);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("dataCadastro", Condicao.BETWEEN, intervalo.inicio(), intervalo.fim())
                .list();

        Map<StatusServico, AcumuladoEtapa> porEtapa = new LinkedHashMap<>();
        for (StatusServico status : StatusServico.values()) {
            porEtapa.put(status, new AcumuladoEtapa());
        }

        for (Servico s : servicos) {
            StatusServico st = s.getStatusServico();
            if (st == null || s.getDataCadastro() == null) continue;

            AcumuladoEtapa acc = porEtapa.get(st);
            acc.quantidade++;

            LocalDate fim = StatusServico.FINALIZADO.equals(st) && s.getDataFinalizacao() != null
                    ? s.getDataFinalizacao()
                    : hoje;
            long dias = ChronoUnit.DAYS.between(s.getDataCadastro().toLocalDate(), fim);
            if (dias < 0) dias = 0;
            acc.somaDias += dias;
        }

        double mediaGeral = 0;
        int etapasComOS = 0;
        for (AcumuladoEtapa acc : porEtapa.values()) {
            if (acc.quantidade > 0) {
                mediaGeral += (double) acc.somaDias / acc.quantidade;
                etapasComOS++;
            }
        }
        if (etapasComOS > 0) mediaGeral /= etapasComOS;

        TabelaRelatorio tabela = new TabelaRelatorio(
                "Produção por Etapa",
                subtituloPeriodo(intervalo),
                new String[]{"Etapa", "Qtd de OS", "Tempo Médio (dias)", "Gargalo"}
        );

        for (Map.Entry<StatusServico, AcumuladoEtapa> e : porEtapa.entrySet()) {
            AcumuladoEtapa acc = e.getValue();
            double media = acc.quantidade == 0 ? 0 : (double) acc.somaDias / acc.quantidade;
            String gargalo = (acc.quantidade > 0 && media > mediaGeral) ? "S" : "N";

            tabela.addRow(
                    e.getKey().name(),
                    acc.quantidade,
                    String.format(BR, "%.1f", media),
                    gargalo
            );
        }

        return renderizar(tabela, filtro);
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioTaxaEntregaNoPrazo(FiltroRelatorioDTO filtro) {
        IntervaloPeriodo intervalo = resolverIntervalo(filtro);

        List<Servico> servicos = dao.select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusServico", Condicao.EQUAL, StatusServico.FINALIZADO)
                .where("dataFinalizacao", Condicao.BETWEEN, intervalo.inicio().toLocalDate(),
                        intervalo.fim().toLocalDate())
                .list();

        Map<YearMonth, AcumuladoPrazo> porMes = new LinkedHashMap<>();
        for (YearMonth ym : iterarMeses(intervalo)) {
            porMes.put(ym, new AcumuladoPrazo());
        }

        for (Servico s : servicos) {
            if (s.getDataFinalizacao() == null) continue;
            YearMonth ym = YearMonth.from(s.getDataFinalizacao());
            AcumuladoPrazo acc = porMes.computeIfAbsent(ym, k -> new AcumuladoPrazo());

            acc.total++;
            if (s.getDataEntregaPrevista() != null
                    && !s.getDataFinalizacao().isAfter(s.getDataEntregaPrevista())) {
                acc.noPrazo++;
            } else {
                acc.atrasadas++;
            }
        }

        TabelaRelatorio tabela = new TabelaRelatorio(
                "Taxa de Entrega no Prazo",
                subtituloPeriodo(intervalo),
                new String[]{"Período", "Total OS", "Entregues no Prazo", "Atrasadas", "Taxa %"}
        );

        for (Map.Entry<YearMonth, AcumuladoPrazo> e : porMes.entrySet()) {
            AcumuladoPrazo acc = e.getValue();
            String taxa = "-";
            if (acc.total > 0) {
                BigDecimal pct = BigDecimal.valueOf(acc.noPrazo)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(acc.total), 2, RoundingMode.HALF_UP);
                taxa = pct.toPlainString() + "%";
            }
            tabela.addRow(
                    e.getKey().format(MES_ANO_BR),
                    acc.total,
                    acc.noPrazo,
                    acc.atrasadas,
                    taxa
            );
        }

        return renderizar(tabela, filtro);
    }

    // =========================================================
    // Renderização
    // =========================================================

    private byte[] renderizar(TabelaRelatorio tabela, FiltroRelatorioDTO filtro) {
        String formato = filtro == null || filtro.formato() == null
                ? FORMATO_PDF
                : filtro.formato().toUpperCase();

        return switch (formato) {
            case FORMATO_CSV -> renderCsv(tabela);
            case FORMATO_XLSX -> renderXlsx(tabela);
            default -> renderPdf(tabela);
        };
    }

    private byte[] renderPdf(TabelaRelatorio tabela) {
        ReportService report = new ReportService()
                .setTitle(tabela.titulo)
                .setSubtitle(tabela.subtitulo)
                .titleUpperCase()
                .colUpperCase()
                .setColorCabecalho(ReportService.GRAY_200)
                .createCol(tabela.colunas);

        for (Object[] row : tabela.linhas) {
            report.createRow(row);
        }
        return report.getBytesPDF();
    }

    private byte[] renderXlsx(TabelaRelatorio tabela) {
        WorkService work = new WorkService()
                .setAba("Relatório")
                .setTitle(tabela.titulo)
                .titleUpperCase()
                .colUpperCase()
                .setColorCabecalho(WorkService.GRAY_200)
                .createCol(tabela.colunas);

        for (Object[] row : tabela.linhas) {
            work.createRow(row);
        }
        return work.getBytesXLSX();
    }

    private byte[] renderCsv(TabelaRelatorio tabela) {
        CsvService.Builder builder = CsvService.export().headers(tabela.colunas);
        for (Object[] row : tabela.linhas) {
            builder.row(row);
        }
        return builder.getBytes();
    }

    // =========================================================
    // Helpers
    // =========================================================

    private IntervaloPeriodo resolverIntervalo(FiltroRelatorioDTO filtro) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = filtro != null && filtro.dataInicio() != null
                ? filtro.dataInicio()
                : hoje.withDayOfMonth(1);
        LocalDate fim = filtro != null && filtro.dataFim() != null
                ? filtro.dataFim()
                : hoje;

        if (fim.isBefore(inicio)) {
            LocalDate tmp = inicio;
            inicio = fim;
            fim = tmp;
        }

        return new IntervaloPeriodo(inicio.atStartOfDay(), fim.atTime(23, 59, 59));
    }

    private List<YearMonth> iterarMeses(IntervaloPeriodo intervalo) {
        List<YearMonth> meses = new ArrayList<>();
        YearMonth atual = YearMonth.from(intervalo.inicio());
        YearMonth fim = YearMonth.from(intervalo.fim());

        while (!atual.isAfter(fim)) {
            meses.add(atual);
            atual = atual.plusMonths(1);
        }
        return meses;
    }

    private String subtituloPeriodo(IntervaloPeriodo intervalo) {
        return "Período: " + intervalo.inicio().toLocalDate().format(DATE_BR)
                + " a " + intervalo.fim().toLocalDate().format(DATE_BR);
    }

    private String nomePessoa(Pessoa p) {
        if (p == null || p.getNome() == null || p.getNome().isBlank()) return "Sem cliente";
        return p.getNome();
    }

    private String moeda(BigDecimal valor) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
        return "R$ " + v.toPlainString().replace('.', ',');
    }

    private String fmt(LocalDateTime dt) {
        return dt == null ? "-" : dt.toLocalDate().format(DATE_BR);
    }

    private String fmt(LocalDate d) {
        return d == null ? "-" : d.format(DATE_BR);
    }

    private String textoOuTraco(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    // =========================================================
    // Estruturas internas
    // =========================================================

    private record IntervaloPeriodo(LocalDateTime inicio, LocalDateTime fim) {
    }

    private static final class TabelaRelatorio {
        final String titulo;
        final String subtitulo;
        final String[] colunas;
        final List<Object[]> linhas = new ArrayList<>();

        TabelaRelatorio(String titulo, String subtitulo, String[] colunas) {
            this.titulo = titulo;
            this.subtitulo = subtitulo;
            this.colunas = colunas;
        }

        void addRow(Object... valores) {
            linhas.add(valores);
        }
    }

    private static final class AcumuladoCliente {
        final String nome;
        long quantidade;
        BigDecimal total = BigDecimal.ZERO;

        AcumuladoCliente(String nome) {
            this.nome = nome == null ? "Sem cliente" : nome;
        }
    }

    private static final class AcumuladoMes {
        long quantidade;
        BigDecimal realizada = BigDecimal.ZERO;
        BigDecimal prevista = BigDecimal.ZERO;
    }

    private static final class AcumuladoComparativo {
        BigDecimal faturamento = BigDecimal.ZERO;
        long abertas;
        long entregues;
    }

    private static final class AcumuladoTicket {
        BigDecimal total = BigDecimal.ZERO;
        long quantidade;
        BigDecimal maior;
        BigDecimal menor;
    }

    private static final class AcumuladoEtapa {
        long quantidade;
        long somaDias;
    }

    private static final class AcumuladoPrazo {
        long total;
        long noPrazo;
        long atrasadas;
    }
}
