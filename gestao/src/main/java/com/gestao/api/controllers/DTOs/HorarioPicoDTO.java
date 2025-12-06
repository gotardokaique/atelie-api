package com.gestao.api.controllers.DTOs; // Ou seu pacote de DTOs

public record HorarioPicoDTO(
    int hora,         // A hora do dia (ex: 6, 7, ..., 18)
    long quantidade   // Quantidade de serviços/clientes naquela hora
) {}
