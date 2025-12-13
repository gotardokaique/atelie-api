package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

public record ResumoFinanceiroDTO(
    BigDecimal valorTotal,
    long quantidadeFinalizados 
) {}