package com.gestao.api.controllers.DTOs;

import com.gestao.api.entities.Configuracao;

public record ConfiguracaoDTO(
        String telefone,
        String tema,
        String paleta,
        String papelParede,
        String densidade,
        String idioma,
        String formatoData,
        String moeda,
        String fusoHorario,
        String paginaInicial,
        Boolean notificarPrazo,
        Integer diasAntecedenciaPrazo,
        Boolean notificarPagamentoPendente,
        Boolean assistenteAtivo,
        String assistenteVoz
) {

    public static ConfiguracaoDTO refactor(Configuracao c) {
        return new ConfiguracaoDTO(
                c.getTelefone(),
                c.getTema(),
                c.getPaleta(),
                c.getPapelParede(),
                c.getDensidade(),
                c.getIdioma(),
                c.getFormatoData(),
                c.getMoeda(),
                c.getFusoHorario(),
                c.getPaginaInicial(),
                c.getNotificarPrazo(),
                c.getDiasAntecedenciaPrazo(),
                c.getNotificarPagamentoPendente(),
                c.getAssistenteAtivo(),
                c.getAssistenteVoz()
        );
    }
}
