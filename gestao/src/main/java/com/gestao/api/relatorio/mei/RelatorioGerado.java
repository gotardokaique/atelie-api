package com.gestao.api.relatorio.mei;

public record RelatorioGerado(
        byte[] conteudo,
        String nomeArquivo
) {}
