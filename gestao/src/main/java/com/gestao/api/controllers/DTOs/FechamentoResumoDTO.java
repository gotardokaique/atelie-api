package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumo financeiro de um serviço:
 * total = produtos (preço snapshot) + mão de obra;
 * margem = total − custo dos insumos consumidos (vindo do ledger).
 */
public record FechamentoResumoDTO(
        Long servicoId,
        BigDecimal totalProdutos,
        BigDecimal valorMaoDeObra,
        BigDecimal total,
        BigDecimal custoMateriais,
        BigDecimal margem,
        List<MovimentacaoDTO> materiaisGastos
) {}
