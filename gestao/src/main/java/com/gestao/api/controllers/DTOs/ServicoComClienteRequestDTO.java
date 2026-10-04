package com.gestao.api.controllers.DTOs;

public record ServicoComClienteRequestDTO(
        PessoaDTO cliente,
        ServicoRequestDTO servico
) {}
