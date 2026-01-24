package com.gestao.api.controllers.DTOs;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.gestao.api.entities.Pessoa;

public record PessoaDTO (
        Long id,
        String nome,
        String telefone,
        String medidas
) implements Serializable{

    public static PessoaDTO refactor(Pessoa pessoa) {

        return new PessoaDTO(
                pessoa.getId(),
                pessoa.getNome(),
                pessoa.getTelefone(),
                pessoa.getMedidas()
        );
    }

    public static List<PessoaDTO> refactor(List<Pessoa> pessoas) {
        return pessoas.stream()
                .filter(Objects::nonNull)
                .map(PessoaDTO::refactor)
                .collect(Collectors.toList());
    }
}
