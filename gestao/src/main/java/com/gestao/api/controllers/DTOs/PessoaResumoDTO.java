package com.gestao.api.controllers.DTOs;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.gestao.api.entities.Pessoa;

public record PessoaResumoDTO(
        Long id,
        String nome
) implements Serializable{

    public static PessoaResumoDTO refactor(Pessoa pessoa) {
        return new PessoaResumoDTO(
                pessoa.getId(),
                pessoa.getNome()
        );
    }

    public static List<PessoaResumoDTO> refactor(List<Pessoa> pessoas) {
        return pessoas.stream()
                .filter(Objects::nonNull)
                .map(PessoaResumoDTO::refactor)
                .collect(Collectors.toList());
    }
}
