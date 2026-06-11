package com.gestao.api.config.lia.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gestao.api.config.lia.tools.LiaFields;
import com.gestao.api.config.lia.tools.LiaTool;

/**
 * Gera o resumo humanizado mostrado ao usuário antes de confirmar uma ação de
 * escrita. Sempre por NOME, nunca por ID — coerente com a regra de ouro.
 */
@Component
public class PreviewBuilder {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String build(LiaTool tool, Map<String, Object> input) {
        return switch (tool) {
            case CRIAR_ORDEM_SERVICO -> criar(input);
            case EDITAR_ORDEM_SERVICO -> editar(input);
            case CADASTRAR_CLIENTE -> cadastrar(input);
            default -> "Deseja realizar essa ação?";
        };
    }

    private String criar(Map<String, Object> input) {
        String descricao = str(input.get(LiaFields.DESCRICAO));
        String cliente = str(input.getOrDefault(LiaFields.CLIENTE_REAL_NOME,
                input.get(LiaFields.CLIENTE_NOME)));

        StringBuilder sb = new StringBuilder()
                .append("Vou criar o serviço de *").append(descricao)
                .append("* para *").append(cliente).append("*");

        BigDecimal valor = bigDecimal(input.get(LiaFields.VALOR));
        if (valor != null) {
            sb.append(String.format(" — R$ %.2f", valor));
        }
        LocalDate data = date(input.get(LiaFields.PRAZO_ENTREGA));
        if (data != null) {
            sb.append(" — entrega ").append(BR.format(data));
        }
        return sb.append(". Confirma?").toString();
    }

    private String editar(Map<String, Object> input) {
        // os_ref já foi resolvido; mostramos o nome amigável guardado no preview
        String alvo = str(input.getOrDefault("os_label", input.get(LiaFields.OS_REF)));

        @SuppressWarnings("unchecked")
        Map<String, Object> campos = (Map<String, Object>) input.get(LiaFields.CAMPOS);

        StringBuilder sb = new StringBuilder("Vou atualizar o serviço de *").append(alvo).append("*");
        if (campos != null) {
            BigDecimal valor = bigDecimal(campos.get(LiaFields.VALOR));
            if (valor != null) {
                sb.append(String.format(" — novo valor R$ %.2f", valor));
            }
            LocalDate data = date(campos.get(LiaFields.PRAZO_ENTREGA));
            if (data != null) {
                sb.append(" — nova entrega ").append(BR.format(data));
            }
            if (campos.get(LiaFields.STATUS) != null) {
                sb.append(" — status ").append(str(campos.get(LiaFields.STATUS)));
            }
        }
        return sb.append(". Confirma?").toString();
    }

    private String cadastrar(Map<String, Object> input) {
        String nome = str(input.get(LiaFields.NOME));
        String tel = str(input.get(LiaFields.TELEFONE));
        return String.format("Vou cadastrar o cliente *%s* com telefone %s. Confirma?",
                nome, tel != null ? tel : "(não informado)");
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private BigDecimal bigDecimal(Object o) {
        if (o == null || o.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate date(Object o) {
        if (o == null || o.toString().isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
