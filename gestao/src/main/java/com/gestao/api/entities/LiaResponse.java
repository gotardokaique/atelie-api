package com.gestao.api.entities;

import java.util.Map;

import com.gestao.api.config.lia.tools.LiaFields;

/**
 * Resposta tipada da Lia ao frontend. Substitui o Map.of(...) solto do código
 * antigo por factory methods explícitos, e converte para Map só na borda (toMap)
 * para manter compatibilidade com o contrato JSON existente.
 */
public record LiaResponse(
        String tipo,
        String resposta,
        String resumo,
        String toolName,
        Map<String, Object> toolInput) {

    public static LiaResponse finalText(String texto) {
        return new LiaResponse(LiaFields.TIPO_FINAL, texto, null, null, null);
    }

    public static LiaResponse error(String texto) {
        return new LiaResponse(LiaFields.TIPO_ERROR, texto, null, null, null);
    }

    public static LiaResponse preview(String resumo, String toolName, Map<String, Object> toolInput) {
        return new LiaResponse(LiaFields.TIPO_PREVIEW, null, resumo, toolName, toolInput);
    }

    /** Texto final do assistant — único tipo que entra no histórico de contexto. */
    public boolean isFinalText() {
        return LiaFields.TIPO_FINAL.equals(tipo);
    }

    /** Serializa para o formato JSON esperado pelo frontend (sem campos nulos). */
    public Map<String, Object> toMap() {
        var map = new java.util.HashMap<String, Object>();
        map.put(LiaFields.TIPO, tipo);
        if (resposta != null) {
            map.put(LiaFields.RESPOSTA, resposta);
        }
        if (resumo != null) {
            map.put(LiaFields.RESUMO, resumo);
        }
        if (toolName != null) {
            map.put(LiaFields.TOOL_NAME, toolName);
        }
        if (toolInput != null) {
            map.put(LiaFields.TOOL_INPUT, toolInput);
        }
        return map;
    }
}
