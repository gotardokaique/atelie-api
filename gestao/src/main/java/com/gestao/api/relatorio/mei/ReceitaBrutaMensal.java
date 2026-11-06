package com.gestao.api.relatorio.mei;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record ReceitaBrutaMensal(
        YearMonth competencia,
        BigDecimal comercioComDispensa,
        BigDecimal comercioComDocumento,
        BigDecimal industriaComDispensa,
        BigDecimal industriaComDocumento,
        BigDecimal servicosComDispensa,
        BigDecimal servicosComDocumento,
        boolean parcial,
        LocalDate apuradoAte,
        String identificador
) {

    public BigDecimal totalComercio() {
        return comercioComDispensa.add(comercioComDocumento);
    }

    public BigDecimal totalIndustria() {
        return industriaComDispensa.add(industriaComDocumento);
    }

    public BigDecimal totalServicos() {
        return servicosComDispensa.add(servicosComDocumento);
    }

    public BigDecimal totalGeral() {
        return totalComercio().add(totalIndustria()).add(totalServicos());
    }
}
