package com.gestao.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gen.core.security.exception.BusinessException;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.MeiMesDTO;
import com.gestao.api.controllers.DTOs.MeiResumoDTO;
import com.gestao.api.entities.Atelie;
import com.gestao.api.entities.LimiteMei;
import com.gestao.api.entities.Servico;
import com.gestao.api.enuns.StatusPagamento;

@Service
public class MeiService {

    private static final int ANO_MINIMO = 2000;
    private static final int ANO_MAXIMO = 2100;
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal DOZE = new BigDecimal("12");

    private final DAOController daoController;
    private final Clock clock;

    public MeiService(DAOController daoController, Clock clock) {
        this.daoController = daoController;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeiResumoDTO obterResumo(Integer anoSolicitado) {
        int anoAtual = LocalDate.now(clock).getYear();
        int ano = anoSolicitado != null ? anoSolicitado : anoAtual;

        if (ano < ANO_MINIMO || ano > ANO_MAXIMO) {
            throw new BusinessException("Ano inválido.");
        }

        LimiteAplicado limite = buscarLimite(ano);
        LocalDate dataAbertura = buscarDataAberturaMei();

        BigDecimal limiteProporcional = calcularLimiteProporcional(limite.valor(), dataAbertura, ano);
        BigDecimal limiteAplicavel = limiteProporcional != null ? limiteProporcional : limite.valor();

        BigDecimal[] valoresMensais = receitaServicosPorMes(ano);
        List<MeiMesDTO> meses = montarSerieMensal(valoresMensais);

        BigDecimal faturamentoServicos = somar(valoresMensais);
        BigDecimal faturamentoComercio = BigDecimal.ZERO.setScale(2);
        BigDecimal faturamentoTotal = faturamentoServicos.add(faturamentoComercio);

        BigDecimal restante = limiteAplicavel.subtract(faturamentoTotal).max(BigDecimal.ZERO);
        BigDecimal excesso = faturamentoTotal.subtract(limiteAplicavel).max(BigDecimal.ZERO);
        BigDecimal percentual = BigDecimal.ZERO.setScale(2);
        if (limiteAplicavel.signum() > 0) {
            percentual = faturamentoTotal.multiply(CEM).divide(limiteAplicavel, 2, RoundingMode.HALF_UP);
        }

        return new MeiResumoDTO(
                ano,
                limite.valor(),
                limite.estimado(),
                limite.observacao(),
                dataAbertura,
                limiteProporcional,
                limiteAplicavel,
                faturamentoTotal,
                faturamentoServicos,
                faturamentoComercio,
                restante,
                excesso,
                percentual,
                meses,
                listarAnosDisponiveis(anoAtual));
    }

    private LimiteAplicado buscarLimite(int ano) {
        List<LimiteMei> limites = daoController
                .select()
                .from(LimiteMei.class)
                .orderBy("ano", true)
                .list();

        if (limites.isEmpty()) {
            throw new BusinessException("Nenhum limite do MEI cadastrado.");
        }

        LimiteMei referencia = limites.get(0);
        for (LimiteMei limite : limites) {
            if (limite.getAno() == ano) {
                return new LimiteAplicado(limite.getValorLimite(), false, limite.getObservacao());
            }
            if (limite.getAno() < ano) {
                referencia = limite;
            }
        }

        String observacao = "Ainda não há limite cadastrado para " + ano
                + ". Estamos usando o limite de " + referencia.getAno() + " como estimativa.";
        return new LimiteAplicado(referencia.getValorLimite(), true, observacao);
    }

    private LocalDate buscarDataAberturaMei() {
        List<Atelie> atelies = daoController
                .select()
                .from(Atelie.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .limit(1)
                .list();

        if (atelies.isEmpty()) {
            return null;
        }
        return atelies.get(0).getDataAberturaMei();
    }

    private BigDecimal calcularLimiteProporcional(BigDecimal limiteAnual, LocalDate dataAbertura, int ano) {
        if (dataAbertura == null || dataAbertura.getYear() != ano) {
            return null;
        }
        int meses = 12 - dataAbertura.getMonthValue() + 1;
        return limiteAnual
                .divide(DOZE, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(meses))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal[] receitaServicosPorMes(int ano) {
        BigDecimal[] valores = new BigDecimal[12];
        for (int i = 0; i < 12; i++) {
            valores[i] = BigDecimal.ZERO.setScale(2);
        }

        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataFinalizacao", Condicao.BETWEEN, LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31))
                .list();

        for (Servico servico : servicos) {
            if (servico.getValor() == null || servico.getDataFinalizacao() == null) {
                continue;
            }
            int indice = servico.getDataFinalizacao().getMonthValue() - 1;
            valores[indice] = valores[indice].add(servico.getValor());
        }
        return valores;
    }

    private List<MeiMesDTO> montarSerieMensal(BigDecimal[] valoresMensais) {
        List<MeiMesDTO> meses = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < 12; i++) {
            acumulado = acumulado.add(valoresMensais[i]);
            meses.add(new MeiMesDTO(i + 1, valoresMensais[i], acumulado));
        }
        return meses;
    }

    private BigDecimal somar(BigDecimal[] valores) {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (BigDecimal valor : valores) {
            total = total.add(valor);
        }
        return total;
    }

    private List<Integer> listarAnosDisponiveis(int anoAtual) {
        TreeSet<Integer> anos = new TreeSet<>();
        anos.add(anoAtual);
        anos.add(anoAtual + 1);

        Servico maisAntigo = buscarExtremoPago(true);
        Servico maisRecente = buscarExtremoPago(false);
        if (maisAntigo != null && maisRecente != null) {
            int primeiro = maisAntigo.getDataFinalizacao().getYear();
            int ultimo = maisRecente.getDataFinalizacao().getYear();
            for (int ano = primeiro; ano <= ultimo; ano++) {
                anos.add(ano);
            }
        }
        return new ArrayList<>(anos.descendingSet());
    }

    private Servico buscarExtremoPago(boolean maisAntigo) {
        List<Servico> encontrados = daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, UserContext.getIdUsuario())
                .where("statusPagamento", Condicao.EQUAL, StatusPagamento.PAGO)
                .where("dataFinalizacao", Condicao.GREATER_OR_EQUAL, LocalDate.of(ANO_MINIMO, 1, 1))
                .orderBy("dataFinalizacao", maisAntigo)
                .limit(1)
                .list();

        if (encontrados.isEmpty()) {
            return null;
        }
        return encontrados.get(0);
    }

    private record LimiteAplicado(BigDecimal valor, boolean estimado, String observacao) {}
}
