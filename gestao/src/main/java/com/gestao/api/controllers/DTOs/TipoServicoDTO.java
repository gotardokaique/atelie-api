package com.gestao.api.controllers.DTOs;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.gestao.api.entities.TipoServico;

public record TipoServicoDTO(
        Long id,
        String nome
) {

    public static TipoServicoDTO refactor(TipoServico tipo) {
        return new TipoServicoDTO(
                tipo.getId(),
                tipo.getNome()
        );
    }

    public static List<TipoServicoDTO> refactor(List<TipoServico> tipos) {
        return tipos.stream()
                .filter(Objects::nonNull)
                .map(TipoServicoDTO::refactor)
                .collect(Collectors.toList());
    }
}
