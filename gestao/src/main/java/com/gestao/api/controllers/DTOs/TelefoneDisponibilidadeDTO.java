package com.gestao.api.controllers.DTOs;

public record TelefoneDisponibilidadeDTO(
        boolean disponivel,
        Long pessoaId,
        String nome
) {}
