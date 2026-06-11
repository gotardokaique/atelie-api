package com.gestao.api.config.lia.tools;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Definição das tools (JSON schema) enviadas ao Grok. Isolada do orquestrador.
 * Note que editar usa os_ref (referência textual), NÃO os_id — o usuário nunca
 * sabe o ID, então o modelo nunca precisa inventá-lo.
 */
@Component
public class ToolSchemaRegistry {

    private static final String TYPE = "type";
    private static final String OBJECT = "object";
    private static final String STRING = "string";
    private static final String NUMBER = "number";
    private static final String BOOLEAN = "boolean";
    private static final String INTEGER = "integer";
    private static final String FUNCTION = "function";
    private static final String DESC = "description";
    private static final String PROPS = "properties";
    private static final String REQUIRED = "required";

    public List<Map<String, Object>> definitions() {
        return List.of(
                fn(LiaTool.BUSCAR_CLIENTES.wireName(),
                        "Busca clientes pelo nome.",
                        schema(null, Map.of(
                                LiaFields.NOME, prop(STRING, "Nome para busca"),
                                LiaFields.LIMITE, prop(INTEGER, "Máximo de resultados")))),

                fn(LiaTool.BUSCAR_ORDENS_SERVICO.wireName(),
                        "Busca ordens de serviço por cliente ou status.",
                        schema(null, Map.of(
                                LiaFields.CLIENTE_NOME, prop(STRING, "Filtro por nome do cliente"),
                                LiaFields.STATUS, prop(STRING,
                                        "PENDENTE, EM_ANDAMENTO, FINALIZADO ou URGENTE"),
                                LiaFields.LIMITE, prop(INTEGER, "Máximo de resultados")))),

                fn(LiaTool.CRIAR_ORDEM_SERVICO.wireName(),
                        "Cria uma nova ordem de serviço. Valor e data vão em campos próprios, NUNCA na descrição.",
                        schema(List.of(LiaFields.CLIENTE_NOME, LiaFields.DESCRICAO), Map.of(
                                LiaFields.CLIENTE_NOME, prop(STRING, "Nome do cliente"),
                                LiaFields.DESCRICAO, prop(STRING, "Apenas a tarefa. Sem valor, sem data."),
                                LiaFields.VALOR, prop(NUMBER, "Valor em reais. Ex: 200.00"),
                                LiaFields.PRAZO_ENTREGA, prop(STRING, "Formato YYYY-MM-DD"),
                                LiaFields.URGENTE, prop(BOOLEAN, "Se é urgente")))),

                fn(LiaTool.EDITAR_ORDEM_SERVICO.wireName(),
                        "Edita uma OS existente. Identifique a OS por os_ref (ex: 'vestido da Ana'); o sistema resolve o ID.",
                        schema(List.of(LiaFields.OS_REF), Map.of(
                                LiaFields.OS_REF, prop(STRING,
                                        "Referência textual da OS: cliente + descrição. Ex: 'vestido da Ana'"),
                                LiaFields.CAMPOS, Map.of(
                                        TYPE, OBJECT,
                                        PROPS, Map.of(
                                                LiaFields.DESCRICAO, prop(STRING, "Nova descrição"),
                                                LiaFields.STATUS, prop(STRING, "Novo status"),
                                                LiaFields.PRAZO_ENTREGA, prop(STRING, "YYYY-MM-DD"),
                                                LiaFields.VALOR, prop(NUMBER, "Novo valor"),
                                                LiaFields.URGENTE, prop(BOOLEAN, "Urgente")))))),

                fn(LiaTool.CADASTRAR_CLIENTE.wireName(),
                        "Cadastra um novo cliente.",
                        schema(List.of(LiaFields.NOME, LiaFields.TELEFONE), Map.of(
                                LiaFields.NOME, prop(STRING, "Nome completo"),
                                LiaFields.TELEFONE, prop(STRING, "Apenas números"),
                                LiaFields.MEDIDAS, prop(STRING, "Medidas ou observações")))));
    }

    private Map<String, Object> fn(String name, String description, Map<String, Object> parameters) {
        return Map.of(TYPE, FUNCTION, FUNCTION, Map.of(
                "name", name,
                DESC, description,
                "parameters", parameters));
    }

    private Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
        if (required == null) {
            return Map.of(TYPE, OBJECT, PROPS, properties);
        }
        return Map.of(TYPE, OBJECT, REQUIRED, required, PROPS, properties);
    }

    private Map<String, Object> prop(String type, String description) {
        return Map.of(TYPE, type, DESC, description);
    }
}
