package com.gestao.api.controllers.DTOs;

import com.gestao.api.entities.Atelie;

public record AtelieDTO(
        String nome,
        String logo,
        String endereco,
        String cnpjCpf,
        String contatoPublico,
        String horarioFuncionamento,
        String dataAberturaMei,
        String nomeResponsavel,
        String cidade,
        String uf
) {

    public static AtelieDTO refactor(Atelie a) {
        return new AtelieDTO(
                a.getNome(),
                a.getLogo(),
                a.getEndereco(),
                a.getCnpjCpf(),
                a.getContatoPublico(),
                a.getHorarioFuncionamento(),
                a.getDataAberturaMei() != null ? a.getDataAberturaMei().toString() : null,
                a.getNomeResponsavel(),
                a.getCidade(),
                a.getUf()
        );
    }
}
