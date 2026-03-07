package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.exceptions.BusinessException;

@Service
public class AiService {

    @Value("${xai.api.key}")
    private String xaiApiKey;

    private final PessoaService pessoaService;
    private final ServicoService servicoService;
    private final ObjectMapper objectMapper;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiService.class);

    public AiService(PessoaService pessoaService, ServicoService servicoService, ObjectMapper objectMapper) {
        this.pessoaService = pessoaService;
        this.servicoService = servicoService;
        this.objectMapper = objectMapper;
    }

    private static final String SYSTEM_PROMPT = """
            Você é a Lia, assistente de voz do Gestão Ateliê. 
            Responda sempre em português brasileiro de forma ultra-objetiva e profissional.
            
            REGRAS CRÍTICAS DE SEGURANÇA E FLUXO:
            1. NUNCA invente ou exiba resumos detalhados (com listas de campos, status ou valores) por conta própria.
            2. Para QUALQUER ação de criar ou editar, você deve APENAS chamar a ferramenta (tool) correspondente. Não diga nada antes ou depois.
            3. Se você for criar um serviço, chame 'criar_ordem_servico'. O sistema cuidará de mostrar o resumo ao usuário.
            4. Se você for editar um serviço, chame 'editar_ordem_servico'.
            5. NUNCA mencione termos técnicos como 'ID', 'JSON', 'PENDENTE', 'EM_PRODUCAO' ou nomes de colunas no chat.
            6. Se não encontrar um cliente, diga apenas: 'Não encontrei o cliente [Nome]. Deseja cadastrar?'.
            """;

    // ─── Entrada principal ────────────────────────────────────────────────────

    public Map<String, Object> processChat(
            List<Map<String, Object>> messages,
            boolean confirmado,
            String toolName,
            Map<String, Object> toolInput) {

        log.info("[Lia] Mensagem recebida do frontend. Mensagens no histórico: {}. Confirmado: {}", messages.size(), confirmado);

        if (confirmado && toolName != null && toolInput != null) {
            log.info("[Lia] Usuário confirmou ação. Executando tool: '{}'", toolName);
            try {
                Object result = executeTool(toolName, toolInput);
                log.info("[Lia] Tool '{}' executada com sucesso.", toolName);
                return Map.of(
                        "tipo", "final",
                        "resposta", formatFinalResponse(toolName, result));
            } catch (Exception e) {
                log.error("[Lia] Erro ao executar tool confirmada '{}': {}", toolName, e.getMessage(), e);
                return Map.of("tipo", "error", "resposta", "Erro ao executar ação: " + e.getMessage());
            }
        }

        return callGrok(messages);
    }

    // ─── Chamada ao Grok ──────────────────────────────────────────────────────

    private Map<String, Object> callGrok(List<Map<String, Object>> messages) {
        WebClient webClient = buildWebClient();

        List<Map<String, Object>> fullMessages = new ArrayList<>();
        fullMessages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        fullMessages.addAll(messages);

        log.info("[Lia] Preparando chamada ao Grok com {} mensagem(ns) no contexto.", fullMessages.size());

        return callGrokWithMessages(webClient, fullMessages);
    }

    private Map<String, Object> callGrokWithMessages(WebClient webClient, List<Map<String, Object>> fullMessages) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "grok-3");
        requestBody.put("messages", fullMessages);
        requestBody.put("tools", getToolsDefinition());
        requestBody.put("tool_choice", "auto");

        try {
            log.info("[Lia] Enviando requisição ao Grok... aguardando resposta.");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("[Lia] Grok respondeu.");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

            if (toolCalls != null && !toolCalls.isEmpty()) {
                Map<String, Object> toolCall = toolCalls.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                String name = (String) function.get("name");

                log.info("[Lia] Grok quer chamar a tool: '{}'", name);

                @SuppressWarnings("unchecked")
                Map<String, Object> arguments = objectMapper.readValue(
                        (String) function.get("arguments"), Map.class);

                log.info("[Lia] Argumentos recebidos para '{}': {}", name, arguments);

                if (isWriteTool(name)) {
                    log.info("[Lia] Ação de escrita detectada. Validando dados antes do preview.");
                    
                    if ("cadastrar_cliente".equals(name)) {
                        return Map.of(
                                "tipo", "preview",
                                "resumo", gerarResumoPreview(name, arguments),
                                "toolName", name,
                                "toolInput", arguments);
                    }

                    // Se for criação de OS, vamos TENTAR buscar o cliente real no banco AGORA
                    if ("criar_ordem_servico".equals(name)) {
                        String clienteNome = (String) arguments.get("cliente_nome");
                        List<PessoaResumoDTO> todosClientes = pessoaService.listarClientesDoUsuario();
                        
                        // Busca flexível: primeiro tenta exato, depois contém
                        Optional<PessoaResumoDTO> clienteOpt = todosClientes.stream()
                                .filter(c -> c.nome().equalsIgnoreCase(clienteNome))
                                .findFirst();
                        
                        if (clienteOpt.isEmpty()) {
                            clienteOpt = todosClientes.stream()
                                    .filter(c -> c.nome().toLowerCase().contains(clienteNome.toLowerCase()))
                                    .findFirst();
                        }
                        
                        if (clienteOpt.isEmpty()) {
                            log.warn("[Lia] Cliente '{}' não encontrado even with flexible search.", clienteNome);
                            return Map.of("tipo", "final", "resposta", 
                                "Não encontrei o cliente '" + clienteNome + "'. Gostaria de cadastrá-lo antes?");
                        }
                        
                        // Atualiza o nome para o nome exato do banco de dados e salva o ID para garantir
                        PessoaResumoDTO clienteReal = clienteOpt.get();
                        arguments.put("cliente_real_nome", clienteReal.nome());
                        arguments.put("pessoa_id_validado", clienteReal.id());
                    }

                    return Map.of(
                            "tipo", "preview",
                            "resumo", gerarResumoPreview(name, arguments),
                            "toolName", name,
                            "toolInput", arguments);
                }

                log.info("[Lia] Ação de leitura. Executando '{}' diretamente.", name);
                Object result = executeTool(name, arguments);
                log.info("[Lia] '{}' executada. Reenviando resultado ao Grok para gerar resposta final.", name);

                fullMessages.add(message);
                fullMessages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCall.get("id"),
                        "name", name,
                        "content", objectMapper.writeValueAsString(result)));

                return callGrokWithMessages(webClient, fullMessages);
            }

            String conteudo = (String) message.getOrDefault("content", "");

            if (conteudo == null || conteudo.isBlank()) {
                log.warn("[Lia] Grok retornou resposta vazia.");
                return Map.of("tipo", "final", "resposta", "Não consegui gerar uma resposta. Tente novamente.");
            }

            log.info("[Lia] Resposta final gerada. Enviando ao frontend.");
            return Map.of("tipo", "final", "resposta", conteudo);

        } catch (Exception e) {
            log.error("[Lia] Erro na comunicação com o Grok: {}", e.getMessage(), e);
            return Map.of("tipo", "error", "resposta", "Desculpe, tive um problema ao processar sua solicitação.");
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private WebClient buildWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.x.ai/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + xaiApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private boolean isWriteTool(String name) {
        return "criar_ordem_servico".equals(name) || "editar_ordem_servico".equals(name) || "cadastrar_cliente".equals(name);
    }

    private Object executeTool(String name, Map<String, Object> input) {
        log.info("[Lia] Executando tool local: '{}'", name);
        return switch (name) {
            case "buscar_clientes" -> {
                String nome = (String) input.get("nome");
                int limite = Integer.parseInt(input.getOrDefault("limite", 10).toString());
                log.info("[Lia] Buscando clientes. Filtro: '{}', limite: {}", nome, limite);
                yield pessoaService.listarClientesDoUsuario().stream()
                        .filter(c -> nome == null || c.nome().toLowerCase().contains(nome.toLowerCase()))
                        .limit(limite)
                        .toList();
            }
            case "buscar_ordens_servico" -> {
                String clienteNome = (String) input.get("cliente_nome");
                String status = (String) input.get("status");
                int limite = Integer.parseInt(input.getOrDefault("limite", 10).toString());
                log.info("[Lia] Buscando ordens. Filtro cliente: '{}', status: '{}', limite: {}", clienteNome, status, limite);
                List<ServicoResponseDTO> servicos = servicoService.listarServicosEmAberto();
                if (clienteNome != null) {
                    servicos = servicos.stream()
                            .filter(s -> s.pessoaNome().toLowerCase().contains(clienteNome.toLowerCase()))
                            .toList();
                }
                if (status != null) {
                    servicos = servicos.stream()
                            .filter(s -> s.statusServico().name().equals(status))
                            .toList();
                }
                yield servicos.stream().limit(limite).toList();
            }
            case "criar_ordem_servico" -> criarOrdemServico(input);
            case "editar_ordem_servico" -> editarOrdemServico(input);
            case "cadastrar_cliente" -> cadastrarCliente(input);
            default -> throw new BusinessException("Ação desconhecida: " + name);
        };
    }

    private String cadastrarCliente(Map<String, Object> input) {
        String nome = (String) input.get("nome");
        String telefone = (String) input.get("telefone");
        String medidas = (String) input.get("medidas");

        PessoaDTO dto = new PessoaDTO(null, nome, telefone, medidas, null);
        pessoaService.criarPessoa(dto);
        
        log.info("[Lia] Cliente '{}' cadastrado com sucesso.", nome);
        return "Cliente " + nome + " cadastrado com sucesso!";
    }

    private String criarOrdemServico(Map<String, Object> input) {
        String clienteNome = (String) input.getOrDefault("cliente_real_nome", input.get("cliente_nome"));
        Long pessoaId = input.containsKey("pessoa_id_validado") 
                ? Long.valueOf(input.get("pessoa_id_validado").toString()) 
                : null;

        log.info("[Lia] Criando OS para cliente: '{}' (id: {})", clienteNome, pessoaId);

        if (pessoaId == null) {
            final String nomeBusca = clienteNome;
            PessoaResumoDTO cliente = pessoaService.listarClientesDoUsuario().stream()
                    .filter(c -> c.nome().equalsIgnoreCase(nomeBusca) || c.nome().toLowerCase().contains(nomeBusca.toLowerCase()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Cliente não encontrado: " + nomeBusca));
            pessoaId = cliente.id();
            clienteNome = cliente.nome();
        }

        ServicoRequestDTO request = new ServicoRequestDTO(
                pessoaId,
                (String) input.get("descricao_servico"),
                parseDateSafely(input.get("prazo_entrega")),
                input.get("valor") != null ? new BigDecimal(input.get("valor").toString()) : null,
                input.get("urgente") != null ? (Boolean) input.get("urgente") : false);

        servicoService.criarServico(request);
        log.info("[Lia] OS criada com sucesso para '{}'.", clienteNome);
        return "Ordem de serviço criada com sucesso para " + clienteNome + ".";
    }

    private String editarOrdemServico(Map<String, Object> input) {
        Long id = Long.valueOf(input.get("os_id").toString());
        log.info("[Lia] Editando OS id: {}", id);

        @SuppressWarnings("unchecked")
        Map<String, Object> campos = (Map<String, Object>) input.get("campos");

        ServicoResponseDTO atual = servicoService.buscarServicoPorId(id);

        ServicoRequestDTO request = new ServicoRequestDTO(
                atual.pessoaId(),
                campos.getOrDefault("descricao", atual.descricao()).toString(),
                campos.get("prazo_entrega") != null
                        ? parseDateSafely(campos.get("prazo_entrega"))
                        : atual.dataEntregaPrevista(),
                campos.get("valor") != null
                        ? new BigDecimal(campos.get("valor").toString())
                        : atual.valor(),
                campos.get("urgente") != null ? (Boolean) campos.get("urgente") : atual.urgente());

        if (campos.containsKey("statusServico")) {
            log.info("[Lia] Atualizando status da OS {} para '{}'.", id, campos.get("statusServico"));
            servicoService.atualizarStatusServico(id, StatusServico.valueOf(campos.get("statusServico").toString()));
        }

        servicoService.atualizarServicoCompleto(id, request);
        log.info("[Lia] OS {} atualizada com sucesso.", id);
        return "Ordem de serviço #" + id + " atualizada com sucesso.";
    }

    private LocalDate parseDateSafely(Object dateObj) {
        if (dateObj == null || dateObj.toString().isBlank()) {
            return null;
        }
        return LocalDate.parse(dateObj.toString());
    }

    private String gerarResumoPreview(String name, Map<String, Object> input) {
        if ("criar_ordem_servico".equals(name)) {
            String descricao = (String) input.get("descricao_servico");
            String cliente = (String) input.getOrDefault("cliente_real_nome", input.get("cliente_nome"));
            String valorStr = "";
            
            if (input.get("valor") != null) {
                BigDecimal valor = new BigDecimal(input.get("valor").toString());
                valorStr = String.format(", no valor de R$ %.2f", valor);
            }
            
            return String.format("Vou criar um serviço de '%s' para %s%s. Confirma?", 
                                 descricao, cliente, valorStr);
        } else if ("editar_ordem_servico".equals(name)) {
            return String.format("Vou atualizar os dados do serviço #%s conforme solicitado. Confirma?", 
                                 input.get("os_id"));
        } else if ("cadastrar_cliente".equals(name)) {
            String nome = (String) input.get("nome");
            String tel = (String) input.get("telefone");
            return String.format("Vou cadastrar o cliente '%s' com o telefone %s. Confirma?", 
                                 nome, tel != null ? tel : "(não informado)");
        }
        return "Deseja realizar essa ação?";
    }

    private String formatFinalResponse(String toolName, Object result) {
        if (toolName.startsWith("buscar")) {
            return "Aqui está o que encontrei: " + result.toString();
        }
        return result.toString(); // As ferramentas de escrita já retornam strings amigáveis
    }

    // ─── Definição das tools para o Grok ─────────────────────────────────────

    private List<Map<String, Object>> getToolsDefinition() {
        return List.of(
                Map.of("type", "function", "function", Map.of(
                        "name", "buscar_clientes",
                        "description", "Busca clientes pelo nome.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "nome", Map.of("type", "string", "description", "Nome para busca"),
                                        "limite", Map.of("type", "integer", "description", "Máximo de resultados"))))),
                Map.of("type", "function", "function", Map.of(
                        "name", "buscar_ordens_servico",
                        "description", "Busca ordens de serviço por cliente ou status.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "cliente_nome", Map.of("type", "string", "description", "Filtro por cliente"),
                                        "status", Map.of("type", "string", "description", "Filtro por status: PENDENTE, EM_ANDAMENTO, FINALIZADO, URGENTE"),
                                        "limite", Map.of("type", "integer", "description", "Máximo de resultados"))))),
                Map.of("type", "function", "function", Map.of(
                        "name", "criar_ordem_servico",
                        "description", "Cria uma nova ordem de serviço.",
                        "parameters", Map.of(
                                "type", "object",
                                "required", List.of("cliente_nome", "descricao_servico"),
                                "properties", Map.of(
                                        "cliente_nome", Map.of("type", "string"),
                                        "descricao_servico", Map.of("type", "string"),
                                        "prazo_entrega", Map.of("type", "string", "description", "Formato YYYY-MM-DD"),
                                        "valor", Map.of("type", "number"),
                                        "urgente", Map.of("type", "boolean"))))),
                Map.of("type", "function", "function", Map.of(
                        "name", "editar_ordem_servico",
                        "description", "Edita uma ordem de serviço existente.",
                        "parameters", Map.of(
                                "type", "object",
                                "required", List.of("os_id", "campos"),
                                "properties", Map.of(
                                        "os_id", Map.of("type", "integer"),
                                        "campos", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "valor", Map.of("type", "number"),
                                                        "urgente", Map.of("type", "boolean"))))))),
                Map.of("type", "function", "function", Map.of(
                        "name", "cadastrar_cliente",
                        "description", "Cadastra um novo cliente no sistema.",
                        "parameters", Map.of(
                                "type", "object",
                                "required", List.of("nome", "telefone"),
                                "properties", Map.of(
                                        "nome", Map.of("type", "string", "description", "Nome completo do cliente"),
                                        "telefone", Map.of("type", "string", "description", "Apenas números"),
                                        "medidas", Map.of("type", "string", "description", "Medidas ou observações gerais"))))));
    }
}