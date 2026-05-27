package com.gestao.api.controllers.DTOs;

import java.util.List;

import jakarta.validation.Valid;

/**
 * Payload do fechamento do serviço: a lista de materiais realmente consumidos
 * (pré-preenchida pela ficha técnica no front, editável antes de finalizar).
 * Cada item vira uma SAIDA de estoque vinculada ao serviço.
 */
public record FinalizarServicoRequestDTO(
        @Valid
        List<MaterialConsumidoDTO> materiais
) {}
