package com.gestao.api.relatorio.mei;

public record EmitenteRelatorio(
        String nomeAtelie,
        String cnpjFormatado,
        String nomeResponsavel,
        String cidade,
        String uf,
        byte[] logoEmCinza
) {

    public String localidade() {
        if (cidade == null) {
            return null;
        }
        if (uf == null) {
            return cidade;
        }
        return cidade + "/" + uf;
    }
}
