package com.gestao.api.config.lia.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestao.api.client.GrokClient;
import com.gestao.api.config.lia.prompet.LiaPromptProvider;
import com.gestao.api.config.lia.properties.LiaProperties;
import com.gestao.api.config.lia.resolver.ReferenceResolver;
import com.gestao.api.config.lia.tools.LiaFields;
import com.gestao.api.config.lia.tools.LiaTool;
import com.gestao.api.config.lia.tools.ToolSchemaRegistry;
import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.entities.LiaResponse;
import com.gestao.api.extract.ValueDateExtractor;

/**
 * Maestro do fluxo da Lia:
 *   1. recebe mensagens do frontend (ou confirmação de ação pendente)
 *   2. chama o Grok com tools
 *   3. roteia: leitura executa direto (com re-prompt p/ formatar);
 *      escrita resolve referências, gera preview e pede confirmação
 *   4. guarda de profundidade impede recursão infinita de tool calls
 */
@Service
public class LiaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LiaOrchestrator.class);

    private final GrokClient grokClient;
    private final LiaPromptProvider promptProvider;
    private final ToolSchemaRegistry schemaRegistry;
    private final LiaActionService actionService;
    private final PreviewBuilder previewBuilder;
    private final ReferenceResolver resolver;
    private final ValueDateExtractor extractor;
    private final ObjectMapper objectMapper;
    private final LiaProperties props;

    public LiaOrchestrator(GrokClient grokClient,
                           LiaPromptProvider promptProvider,
                           ToolSchemaRegistry schemaRegistry,
                           LiaActionService actionService,
                           PreviewBuilder previewBuilder,
                           ReferenceResolver resolver,
                           ValueDateExtractor extractor,
                           ObjectMapper objectMapper,
                           LiaProperties props) {
        this.grokClient = grokClient;
        this.promptProvider = promptProvider;
        this.schemaRegistry = schemaRegistry;
        this.actionService = actionService;
        this.previewBuilder = previewBuilder;
        this.resolver = resolver;
        this.extractor = extractor;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    // ─── Entrada principal ────────────────────────────────────────────────

    public LiaResponse process(List<Map<String, Object>> messages,
                               boolean confirmado,
                               String toolName,
                               Map<String, Object> toolInput) {

        if (confirmado && toolName != null && toolInput != null) {
            return executarConfirmado(toolName, toolInput);
        }

        List<Map<String, Object>> fullMessages = new ArrayList<>();
        fullMessages.add(Map.of("role", "system", "content", promptProvider.systemPrompt()));
        fullMessages.addAll(messages);

        return conversar(fullMessages, 0);
    }

    // ─── Confirmação de ação pendente ─────────────────────────────────────

    private LiaResponse executarConfirmado(String toolName, Map<String, Object> toolInput) {
        Optional<LiaTool> tool = LiaTool.fromWireName(toolName);
        if (tool.isEmpty()) {
            return LiaResponse.error("Ação desconhecida: " + toolName);
        }
        try {
            Object result = actionService.execute(tool.get(), toolInput);
            return LiaResponse.finalText(result.toString());
        } catch (Exception e) {
            log.error("[Lia] Erro ao executar ação confirmada '{}': {}", toolName, e.getMessage(), e);
            return LiaResponse.error("Não consegui concluir: " + e.getMessage());
        }
    }

    // ─── Loop conversacional com guarda de recursão ───────────────────────

    private LiaResponse conversar(List<Map<String, Object>> messages, int depth) {
        if (depth >= props.getMaxToolIterations()) {
            log.warn("[Lia] Limite de iterações de tool atingido ({}).", depth);
            return LiaResponse.finalText("Não consegui concluir o raciocínio. Pode reformular?");
        }

        Map<String, Object> response;
        try {
            response = grokClient.chat(messages, schemaRegistry.definitions());
        } catch (Exception e) {
            log.error("[Lia] Falha na comunicação com o Grok: {}", e.getMessage(), e);
            return LiaResponse.error("Tive um problema ao processar. Tente novamente.");
        }

        Map<String, Object> message = extractMessage(response);
        if (message == null) {
            return LiaResponse.finalText("Não consegui gerar uma resposta. Tente novamente.");
        }

        Map<String, Object> toolCall = firstToolCall(message);
        if (toolCall == null) {
            return finalTextFrom(message);
        }

        ParsedCall call = parseCall(toolCall);
        if (call == null) {
            return LiaResponse.error("Não entendi a ação solicitada.");
        }

        sanitizeFallback(call.tool, call.arguments);

        if (call.tool.isWrite()) {
            return prepararEscrita(call);
        }

        // Leitura: executa e devolve o resultado ao modelo para formatar
        Object result = actionService.execute(call.tool, call.arguments);
        messages.add(message);
        messages.add(toolResultMessage(call, result));
        return conversar(messages, depth + 1);
    }

    // ─── Preparação de escrita (resolução de referência + preview) ─────────

    private LiaResponse prepararEscrita(ParsedCall call) {
        switch (call.tool) {
            case CADASTRAR_CLIENTE:
                return preview(call);

            case CRIAR_ORDEM_SERVICO: {
                String nome = str(call.arguments.get(LiaFields.CLIENTE_NOME));
                Optional<PessoaResumoDTO> cliente = resolver.resolverCliente(nome);
                if (cliente.isEmpty()) {
                    return LiaResponse.finalText(
                            "Não encontrei o cliente '" + nome + "'. Quer cadastrá-lo antes?");
                }
                call.arguments.put(LiaFields.CLIENTE_REAL_NOME, cliente.get().nome());
                call.arguments.put(LiaFields.PESSOA_ID, cliente.get().id());
                return preview(call);
            }

            case EDITAR_ORDEM_SERVICO: {
                String osRef = str(call.arguments.get(LiaFields.OS_REF));
                Optional<ServicoResponseDTO> os = resolver.resolverOrdemServico(osRef);
                if (os.isEmpty()) {
                    return LiaResponse.finalText(
                            "Não encontrei o serviço '" + osRef + "'. Pode me dizer o cliente e o que é?");
                }
                call.arguments.put(LiaFields.OS_ID, os.get().id());
                call.arguments.put("os_label", os.get().pessoaNome() + " — " + os.get().descricao());
                return preview(call);
            }

            default:
                return preview(call);
        }
    }

    private LiaResponse preview(ParsedCall call) {
        return LiaResponse.preview(
                previewBuilder.build(call.tool, call.arguments),
                call.tool.wireName(),
                call.arguments);
    }

    // ─── Fallback determinístico (rede de segurança, não fonte concorrente) ─

    private void sanitizeFallback(LiaTool tool, Map<String, Object> args) {
        Map<String, Object> alvo = tool == LiaTool.EDITAR_ORDEM_SERVICO
                ? subMap(args, LiaFields.CAMPOS)
                : args;
        if (alvo == null) {
            return;
        }

        String descricao = str(alvo.get(LiaFields.DESCRICAO));
        if (descricao == null) {
            return;
        }

        if (isEmpty(alvo.get(LiaFields.VALOR))) {
            extractor.extrairValor(descricao).ifPresent(v -> alvo.put(LiaFields.VALOR, v));
        }
        if (isEmpty(alvo.get(LiaFields.PRAZO_ENTREGA))) {
            extractor.extrairData(descricao)
                    .ifPresent(d -> alvo.put(LiaFields.PRAZO_ENTREGA, d.toString()));
        }
    }

    // ─── Parsing de respostas do Grok ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMessage(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return (Map<String, Object>) choices.get(0).get("message");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstToolCall(Map<String, Object> message) {
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        return (toolCalls == null || toolCalls.isEmpty()) ? null : toolCalls.get(0);
    }

    private ParsedCall parseCall(Map<String, Object> toolCall) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String name = (String) function.get("name");
            Optional<LiaTool> tool = LiaTool.fromWireName(name);
            if (tool.isEmpty()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(
                    (String) function.get("arguments"), Map.class);
            return new ParsedCall(tool.get(), args, (String) toolCall.get("id"));
        } catch (Exception e) {
            log.error("[Lia] Falha ao parsear tool call: {}", e.getMessage(), e);
            return null;
        }
    }

    private Map<String, Object> toolResultMessage(ParsedCall call, Object result) {
        try {
            return Map.of(
                    "role", "tool",
                    "tool_call_id", call.id,
                    "name", call.tool.wireName(),
                    "content", objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            return Map.of(
                    "role", "tool",
                    "tool_call_id", call.id,
                    "name", call.tool.wireName(),
                    "content", "[]");
        }
    }

    private LiaResponse finalTextFrom(Map<String, Object> message) {
        Object content = message.get("content");
        String texto = content == null ? "" : content.toString();
        if (texto.isBlank()) {
            return LiaResponse.finalText("Não consegui gerar uma resposta. Tente novamente.");
        }
        return LiaResponse.finalText(texto);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> subMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private boolean isEmpty(Object o) {
        if (o == null) {
            return true;
        }
        String s = o.toString().trim();
        return s.isBlank() || "null".equalsIgnoreCase(s);
    }

    /** Tupla interna para carregar a chamada parseada. */
    private record ParsedCall(LiaTool tool, Map<String, Object> arguments, String id) {
    }
}
