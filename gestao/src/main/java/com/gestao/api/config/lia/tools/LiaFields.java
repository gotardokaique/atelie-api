package com.gestao.api.config.lia.tools;

/**
 * Chaves de campo trocadas entre Grok, orquestrador e services.
 * Centralizadas para acabar com o naming inconsistente do código antigo
 * (onde "descricao_servico" e "descricao" coexistiam e se confundiam).
 *
 * REGRA: criar e editar usam a MESMA chave de descrição (DESCRICAO).
 */
public final class LiaFields {

    private LiaFields() {
    }

    // Campos de resposta ao frontend
    public static final String TIPO = "tipo";
    public static final String RESPOSTA = "resposta";
    public static final String RESUMO = "resumo";
    public static final String TOOL_NAME = "toolName";
    public static final String TOOL_INPUT = "toolInput";

    // Valores de TIPO
    public static final String TIPO_FINAL = "final";
    public static final String TIPO_PREVIEW = "preview";
    public static final String TIPO_ERROR = "error";

    // Campos de domínio (argumentos das tools)
    public static final String CLIENTE_NOME = "cliente_nome";
    public static final String CLIENTE_REAL_NOME = "cliente_real_nome";
    public static final String PESSOA_ID = "pessoa_id";
    public static final String DESCRICAO = "descricao";
    public static final String VALOR = "valor";
    public static final String PRAZO_ENTREGA = "prazo_entrega";
    public static final String URGENTE = "urgente";
    public static final String STATUS = "status";
    public static final String OS_ID = "os_id";
    public static final String OS_REF = "os_ref";
    public static final String LIMITE = "limite";
    public static final String NOME = "nome";
    public static final String TELEFONE = "telefone";
    public static final String MEDIDAS = "medidas";
    public static final String CAMPOS = "campos";
    public static final String AREA = "area";
}
