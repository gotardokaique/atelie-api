package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MeiResumoDTO(
        int ano,
        BigDecimal limiteAnual,
        boolean limiteEstimado,
        String observacaoLimite,
        LocalDate dataAberturaMei,
        BigDecimal limiteProporcional,
        BigDecimal limiteAplicavel,
        BigDecimal faturamentoAcumulado,
        BigDecimal faturamentoServicos,
        BigDecimal faturamentoComercio,
        BigDecimal restante,
        BigDecimal excesso,
        BigDecimal percentualLimiteUsado,
        List<MeiMesDTO> meses,
        List<Integer> anosDisponiveis
) {}
