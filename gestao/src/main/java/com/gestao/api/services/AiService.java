package com.gestao.api.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // Constantes para evitar duplicação e erros de lint
    private static final String TIPO = "tipo";
    private static final String FINAL = "final";
    private static final String PREVIEW = "preview";
    private static final String RESPOSTA = "resposta";
    private static final String RESUMO = "resumo";
    private static final String CONTENT = "content";
    private static final String FUNCTION = "function";
    private static final String CRIAR_OS = "criar_ordem_servico";
    private static final String EDITAR_OS = "editar_ordem_servico";
    private static final String CADASTRAR_CLIENTE = "cadastrar_cliente";
    private static final String CLIENTE_NOME = "cliente_nome";
    private static final String CLIENTE_REAL_NOME = "cliente_real_nome";
    private static final String PESSOA_ID_VALIDADO = "pessoa_id_validado";
    private static final String DESCRICAO_SERVICO = "descricao_servico";
    private static final String VALOR = "valor";
    private static final String PRAZO_ENTREGA = "prazo_entrega";
    private static final String URGENTE = "urgente";
    private static final String OS_ID = "os_id";

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
                        Você é a Lia, assistente inteligente do Gestão Ateliê.
            Responda SEMPRE em português brasileiro. Seja direta, humana e enxuta — como o WhatsApp.

            ═══════════════════════════════════════════════════════════
            IDENTIDADE E TOM
            ═══════════════════════════════════════════════════════════

            - Respostas curtas. Máximo 2 linhas quando possível.
            - Use emojis com moderação para tornar a leitura agradável.
            - NUNCA exiba IDs técnicos ao usuário (os_id, pessoa_id, etc).
            - Referencie sempre por NOME: "serviço de Maria Silva", não "OS #47".
            - Se o usuário confirmar algo, responda apenas: "✅ Feito!" ou similar.
            - Seja proativa: se faltar info, pergunte diretamente. Uma pergunta por vez.

            ═══════════════════════════════════════════════════════════
            EXTRAÇÃO DE VALOR (CRÍTICO — NUNCA IGNORAR)
            ═══════════════════════════════════════════════════════════

            Se QUALQUER mensagem mencionar valor monetário, SEMPRE extrair e passar no parâmetro 'valor'.
            NUNCA colocar valor dentro de 'descricao_servico'.

            Padrões reconhecidos:
              "valor 200"       → valor=200
              "200 reais"       → valor=200
              "R$ 150,50"       → valor=150.50
              "por 100"         → valor=100
              "custa 80"        → valor=80
              "cobrar 250"      → valor=250
              "anota 300"       → valor=300
              "coloca 120"      → valor=120

            Exemplo correto:
              ❌ descricao="ajustar bainha valor 200", valor=null
              ✅ descricao="ajustar bainha", valor=200

            ═══════════════════════════════════════════════════════════
            EXTRAÇÃO DE DATA (CRÍTICO — NUNCA IGNORAR)
            ═══════════════════════════════════════════════════════════

            Se mencionada, SEMPRE extrair e passar em 'prazo_entrega' no formato YYYY-MM-DD.
            NUNCA colocar data dentro de 'descricao_servico'.

              "15/03"            → "2026-03-15" (próximo ano se já passou)
              "15-03-2026"       → "2026-03-15"
              "amanhã"           → data de amanhã
              "próxima segunda"  → YYYY-MM-DD da próxima segunda
              "em 3 dias"        → hoje + 3 dias

            ═══════════════════════════════════════════════════════════
            INTENÇÕES RECONHECIDAS
            ═══════════════════════════════════════════════════════════

            CRIAR → "anota", "registra", "abre", "cria", "coloca", "adiciona", "novo"
            EDITAR → "atualiza", "muda", "corrige", "edita", "altera", "coloca o valor", "anota o valor"
            BUSCAR → "mostra", "lista", "quais", "tem algum", "me diz", "quero ver"
            DELETAR → "cancela", "remove", "apaga", "deleta"

            ⚠️ Se o usuário disser "anota o valor X no serviço Y" → chame editar_ordem_servico com valor=X.
               Primeiro busque a OS pelo nome/descrição, depois edite. Nunca ignore essa intenção.

            ═══════════════════════════════════════════════════════════
            REGRAS DE EXECUÇÃO
            ═══════════════════════════════════════════════════════════

            1. Para CRIAR ou EDITAR → chame APENAS a tool. Sem texto na resposta.
            2. Para BUSCAR → execute a tool e formate o resultado de forma legível, sem IDs.
            3. Se faltar o nome do cliente → pergunte: "Para qual cliente?"
            4. Se faltar descrição → pergunte: "Qual o serviço?"
            5. Se cliente não encontrado → informe pelo nome e pergunte se quer cadastrar.
            6. Nunca invente dados. Nunca complete campos que o usuário não informou.
            7. Na confirmação de preview, mostre os dados de forma humanizada:
               "✅ Serviço de *ajuste de bainha* para *Maria Silva* — R$ 200,00 — entrega 15/03. Confirma?"
                        """;

    // ─── Entrada principal ────────────────────────────────────────────────────

    public Map<String, Object> processChat(
            List<Map<String, Object>> messages,
            boolean confirmado,
            String toolName,
            Map<String, Object> toolInput) {

        log.info("[Lia] Mensagem recebida do frontend. Mensagens no histórico: {}. Confirmado: {}", messages.size(),
                confirmado);

        if (confirmado && toolName != null && toolInput != null) {
            log.info("[Lia] Usuário confirmou ação. Executando tool: '{}'", toolName);
            try {
                Object result = executeTool(toolName, toolInput);
                log.info("[Lia] Tool '{}' executada com sucesso.", toolName);
                return Map.of(
                        TIPO, FINAL,
                        RESPOSTA, formatFinalResponse(toolName, result));
            } catch (Exception e) {
                log.error("[Lia] Erro ao executar tool confirmada '{}': {}", toolName, e.getMessage(), e);
                return Map.of(TIPO, "error", RESPOSTA, "Erro ao executar ação: " + e.getMessage());
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
        requestBody.put("model", "grok-4-fast-non-reasoning");
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

                log.info("[Lia] Argumentos ANTES do pós-processamento para '{}': {}", name, arguments);

                // ✅ Pós-processamento robusta de valor e data ANTES de validar
                sanitizeToolArguments(name, arguments);

                log.info("[Lia] Argumentos DEPOIS do pós-processamento para '{}': {}", name, arguments);

                if (isWriteTool(name)) {
                    log.info("[Lia] Ação de escrita detectada. Validando dados antes do preview.");

                    if (CADASTRAR_CLIENTE.equals(name)) {
                        return Map.of(
                                TIPO, PREVIEW,
                                RESUMO, gerarResumoPreview(name, arguments),
                                "toolName", name,
                                "toolInput", arguments);
                    }

                    // Se for criação de OS, vamos TENTAR buscar o cliente real no banco AGORA
                    if (CRIAR_OS.equals(name)) {
                        String clienteNome = (String) arguments.get(CLIENTE_NOME);
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
                            return Map.of(TIPO, FINAL, RESPOSTA,
                                    "Não encontrei o cliente '" + clienteNome + "'. Gostaria de cadastrá-lo antes?");
                        }

                        // Atualiza o nome para o nome exato do banco de dados e salva o ID para
                        // garantir
                        PessoaResumoDTO clienteReal = clienteOpt.get();
                        arguments.put(CLIENTE_REAL_NOME, clienteReal.nome());
                        arguments.put(PESSOA_ID_VALIDADO, clienteReal.id());
                    }

                    return Map.of(
                            TIPO, PREVIEW,
                            RESUMO, gerarResumoPreview(name, arguments),
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

    // ─── Pós-processamento de argumentos (CRÍTICO) ────────────────────────────

    /**
     * Sanitiza argumentos extraídos do Grok.
     * IMPORTANTE: Se Grok deixou valor/data na descrição, extrai e remove.
     */
    private void sanitizeToolArguments(String toolName, Map<String, Object> arguments) {
        if (!CRIAR_OS.equals(toolName) && !EDITAR_OS.equals(toolName)) {
            return;
        }

        if (EDITAR_OS.equals(toolName)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> campos = (Map<String, Object>) arguments.get("campos");
            if (campos != null) {
                sanitizeDescriptionAndExtractData(campos, "descricao");
            }
            return;
        }

        sanitizeDescriptionAndExtractData(arguments, DESCRICAO_SERVICO);
    }

    /**
     * Extrai valor e data de um campo de descrição.
     * Agressivo: se encontrar padrão claro, extrai mesmo que Grok não tenha
     * conseguido.
     */
    private void sanitizeDescriptionAndExtractData(Map<String, Object> map, String descricaoField) {
        String descricao = (String) map.get(descricaoField);
        if (descricao == null || descricao.isBlank()) {
            return;
        }

        log.info("[Lia] Sanitizando '{}': '{}'", descricaoField, descricao);

        // ✅ Extrair VALOR se não foi passado explicitamente
        if (isEmpty(map.get(VALOR))) {
            BigDecimal valorExtraido = extrairValor(descricao);
            if (valorExtraido != null) {
                String descricaoLimpa = removerValorDaDescricao(descricao);
                map.put(VALOR, valorExtraido);
                map.put(descricaoField, descricaoLimpa);
                log.info("[Lia] VALOR extraído: {} | Descrição: '{}'", valorExtraido, descricaoLimpa);
            }
        }

        // ✅ Extrair DATA se não foi passada explicitamente
        if (isEmpty(map.get(PRAZO_ENTREGA))) {
            LocalDate dataExtraida = extrairData(descricao);
            if (dataExtraida != null) {
                String descricaoLimpa = removerDataDaDescricao(descricao);
                map.put(PRAZO_ENTREGA, dataExtraida.toString());
                map.put(descricaoField, descricaoLimpa);
                log.info("[Lia] DATA extraída: {} | Descrição: '{}'", dataExtraida, descricaoLimpa);
            }
        }
    }

    private boolean isEmpty(Object obj) {
        if (obj == null)
            return true;
        String str = obj.toString().trim();
        return str.isBlank() || "null".equalsIgnoreCase(str);
    }

    /**
     * Extrai VALOR monetário quando mencionado.
     * Procura por padrões: "valor 200", "200 reais", "R$ 150.50", "por 100", "custa
     * 50"
     */
    private BigDecimal extrairValor(String texto) {
        Pattern[] patterns = {
                Pattern.compile("(?i)\\bvalor\\s+([\\d.,]+)"),
                Pattern.compile("(?i)\\b([\\d.,]+)\\s+reais?\\b"),
                Pattern.compile("(?i)r\\$\\s+([\\d.,]+)"),
                Pattern.compile("(?i)\\b(?:custa|por|cobrar)\\s+([\\d.,]+)"),
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(texto);
            if (matcher.find()) {
                String valor = matcher.group(1);
                valor = valor.replace(",", ".");
                try {
                    return new BigDecimal(valor);
                } catch (NumberFormatException e) {
                    log.warn("[Lia] Falha ao converter valor '{}'", valor);
                }
            }
        }
        return null;
    }

    /**
     * Extrai data em diversos formatos.
     * Suporta: "15/03", "15-03", "15/03/2026", "15 de março", "próxima segunda",
     * "amanhã", etc.
     */
    private LocalDate extrairData(String texto) {
        LocalDate hoje = LocalDate.now();

        // Padrão 1: DD/MM/YYYY ou DD/MM ou DD-MM-YYYY ou DD-MM (com ano opcional)
        Pattern dataPadrao = Pattern.compile("(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}))?");
        Matcher matcher = dataPadrao.matcher(texto);
        if (matcher.find()) {
            try {
                int dia = Integer.parseInt(matcher.group(1));
                int mes = Integer.parseInt(matcher.group(2));
                int ano = hoje.getYear();

                // Se ano foi fornecido (grupo 3), usa ele
                if (matcher.group(3) != null) {
                    ano = Integer.parseInt(matcher.group(3));
                } else {
                    // Se a data já passou este ano, assume próximo ano
                    LocalDate dataTemp = LocalDate.of(ano, mes, dia);
                    if (dataTemp.isBefore(hoje)) {
                        ano = ano + 1;
                    }
                }

                LocalDate data = LocalDate.of(ano, mes, dia);
                return data;
            } catch (Exception e) {
                log.warn("[Lia] Erro ao parsear data DD/MM/YYYY: {}", e.getMessage());
            }
        }

        // Padrão 2: "amanhã"
        if (texto.toLowerCase().contains("amanhã")) {
            return hoje.plusDays(1);
        }

        // Padrão 3: "próxima segunda/terça/..." ou "segunda-feira", "terça-feira", etc.
        String textoLower = texto.toLowerCase();
        String[] diasSemana = { "domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado", "sabado" };
        int[] diasAdicionar = { 0, 1, 2, 3, 4, 5, 6, 6 };

        for (int i = 0; i < diasSemana.length; i++) {
            if (textoLower.contains(diasSemana[i])) {
                // Se tem "próxima", força a semana que vem
                int diasAdd = diasAdicionar[i];
                if (textoLower.contains("próxima")) {
                    diasAdd += 7;
                }

                LocalDate data = hoje.plusDays(diasAdd);
                // Ajusta para o próximo dia da semana se necessário
                while (data.getDayOfWeek().getValue() % 7 != i) {
                    data = data.plusDays(1);
                }
                return data;
            }
        }

        // Padrão 4: "em X dias"
        Pattern diasPattern = Pattern.compile("em\\s+(\\d+)\\s+dias");
        Matcher diasMatcher = diasPattern.matcher(texto);
        if (diasMatcher.find()) {
            int dias = Integer.parseInt(diasMatcher.group(1));
            return hoje.plusDays(dias);
        }

        return null;
    }

    /**
     * Remove VALOR mencionado da descrição.
     */
    private String removerValorDaDescricao(String descricao) {
        String resultado = descricao;
        resultado = resultado.replaceAll("(?i)\\bvalor\\s+[\\d.,]+\\b", "");
        resultado = resultado.replaceAll("(?i)\\b[\\d.,]+\\s+reais?\\b", "");
        resultado = resultado.replaceAll("(?i)r\\$\\s+[\\d.,]+", "");
        resultado = resultado.replaceAll("(?i)\\b(?:custa|por|cobrar)\\s+[\\d.,]+\\b", "");
        return resultado.replaceAll("\\s+", " ").trim();
    }

    /**
     * Remove DATA mencionada da descrição.
     */
    private String removerDataDaDescricao(String descricao) {
        String resultado = descricao;
        resultado = resultado.replaceAll("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{4})?\\b", "");
        resultado = resultado.replaceAll("(?i)\\bamanhã\\b", "");
        resultado = resultado.replaceAll("(?i)\\bem\\s+\\d+\\s+dias\\b", "");
        resultado = resultado.replaceAll(
                "(?i)(?:próxima\\s+)?(?:segunda|terça|quarta|quinta|sexta|sábado|sabado|domingo)(?:-feira)?", "");
        return resultado.replaceAll("\\s+", " ").trim();
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
        return CRIAR_OS.equals(name) || EDITAR_OS.equals(name) || CADASTRAR_CLIENTE.equals(name);
    }

    private Object executeTool(String name, Map<String, Object> input) throws Exception {
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
                String clienteFiltro = (String) input.get(CLIENTE_NOME);
                String status = (String) input.get("status");
                int limite = Integer.parseInt(input.getOrDefault("limite", 10).toString());
                log.info("[Lia] Buscando ordens. Filtro cliente: '{}', status: '{}', limite: {}", clienteFiltro, status,
                        limite);
                List<ServicoResponseDTO> servicos = servicoService.listarServicosEmAberto(null);
                if (clienteFiltro != null) {
                    servicos = servicos.stream()
                            .filter(s -> s.pessoaNome().toLowerCase().contains(clienteFiltro.toLowerCase()))
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

    private String cadastrarCliente(Map<String, Object> input) throws Exception {
        String nome = (String) input.get("nome");
        String telefone = (String) input.get("telefone");
        String medidas = (String) input.get("medidas");

        PessoaDTO dto = new PessoaDTO(null, nome, telefone, medidas, null);
        pessoaService.criarPessoa(dto);

        log.info("[Lia] Cliente '{}' cadastrado com sucesso.", nome);
        return "Cliente " + nome + " cadastrado com sucesso!";
    }

    private String criarOrdemServico(Map<String, Object> input) {
        String cliente = (String) input.getOrDefault(CLIENTE_REAL_NOME, input.get(CLIENTE_NOME));
        Long pessoaId = input.containsKey(PESSOA_ID_VALIDADO)
                ? Long.valueOf(input.get(PESSOA_ID_VALIDADO).toString())
                : null;

        log.info("[Lia] Criando OS para cliente: '{}' (id: {})", cliente, pessoaId);

        if (pessoaId == null) {
            final String nomeBusca = cliente;
            PessoaResumoDTO p = pessoaService.listarClientesDoUsuario().stream()
                    .filter(c -> c.nome().equalsIgnoreCase(nomeBusca)
                            || c.nome().toLowerCase().contains(nomeBusca.toLowerCase()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Cliente não encontrado: " + nomeBusca));
            pessoaId = p.id();
            cliente = p.nome();
        }

        ServicoRequestDTO request = new ServicoRequestDTO(
                pessoaId,
                (String) input.get(DESCRICAO_SERVICO),
                parseDateSafely(input.get(PRAZO_ENTREGA)),
                input.get(VALOR) != null ? new BigDecimal(input.get(VALOR).toString()) : null,
                input.get(URGENTE) != null ? (Boolean) input.get(URGENTE) : false,
                null,
                false);

        servicoService.criarServico(request);
        log.info("[Lia] OS criada com sucesso para '{}'.", cliente);
        return "Ordem de serviço criada com sucesso para " + cliente + ".";
    }

    private String editarOrdemServico(Map<String, Object> input) {
        Long id = Long.valueOf(input.get(OS_ID).toString());
        log.info("[Lia] Editando OS id: {}", id);

        @SuppressWarnings("unchecked")
        Map<String, Object> campos = (Map<String, Object>) input.get("campos");

        ServicoResponseDTO atual = servicoService.buscarServicoPorId(id);

        ServicoRequestDTO request = new ServicoRequestDTO(
                atual.pessoaId(),
                campos.getOrDefault("descricao", atual.descricao()).toString(),
                campos.get(PRAZO_ENTREGA) != null
                        ? parseDateSafely(campos.get(PRAZO_ENTREGA))
                        : atual.dataEntregaPrevista(),
                campos.get(VALOR) != null
                        ? new BigDecimal(campos.get(VALOR).toString())
                        : atual.valor(),
                campos.get(URGENTE) != null ? (Boolean) campos.get(URGENTE) : atual.urgente(),
                null,
                false);

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

            StringBuilder resumo = new StringBuilder();
            resumo.append(String.format("Vou criar um serviço de '%s' para %s", descricao, cliente));

            if (input.get("valor") != null && !input.get("valor").toString().isBlank()) {
                try {
                    BigDecimal valor = new BigDecimal(input.get("valor").toString());
                    resumo.append(String.format(", no valor de R$ %.2f", valor));
                } catch (Exception e) {
                }
            }

            LocalDate data = parseDateSafely(input.get("prazo_entrega"));
            if (data != null) {
                resumo.append(String.format(" para o dia %02d/%02d/%d",
                        data.getDayOfMonth(), data.getMonthValue(), data.getYear()));
            }

            resumo.append(". Confirma?");
            return resumo.toString();
        } else if ("editar_ordem_servico".equals(name)) {
            return String.format("Vou atualizar os dados do serviço #%s conforme solicitado. Confirma?",
                    input.get("os_id"));
        } else if ("cadastrar_cliente".equals(name)) {
            String nome = (String) input.get("nome");
            String tel = (String) input.get("telefone");
            return String.format("Vou cadastrar o cliente '%s' com o telefone %s. Confirma?",
                    nome, tel != null && !tel.isBlank() ? tel : "(não informado)");
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
                Map.of("type", FUNCTION, FUNCTION, Map.of(
                        "name", "buscar_clientes",
                        "description", "Busca clientes pelo nome no sistema.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "nome", Map.of("type", "string", "description", "Nome para busca"),
                                        "limite", Map.of("type", "integer", "description", "Máximo de resultados"))))),
                Map.of("type", FUNCTION, FUNCTION, Map.of(
                        "name", "buscar_ordens_servico",
                        "description", "Busca ordens de serviço por cliente ou status.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        CLIENTE_NOME,
                                        Map.of("type", "string", "description", "Filtro por nome do cliente"),
                                        "status",
                                        Map.of("type", "string", "description",
                                                "Filtro por status: PENDENTE, EM_ANDAMENTO, FINALIZADO, URGENTE"),
                                        "limite", Map.of("type", "integer", "description", "Máximo de resultados"))))),
                Map.of("type", FUNCTION, FUNCTION, Map.of(
                        "name", "criar_ordem_servico",
                        "description", "Cria uma nova ordem de serviço. SEMPRE separe valor e data da descrição.",
                        "parameters", Map.of(
                                "type", "object",
                                "required", List.of(CLIENTE_NOME, DESCRICAO_SERVICO),
                                "properties", Map.of(
                                        CLIENTE_NOME, Map.of("type", "string", "description", "Nome do cliente"),
                                        DESCRICAO_SERVICO,
                                        Map.of("type", "string", "description",
                                                "Apenas o texto da tarefa. NÃO inclua o valor aqui."),
                                        PRAZO_ENTREGA, Map.of("type", "string", "description", "Formato YYYY-MM-DD"),
                                        VALOR,
                                        Map.of("type", "number", "description",
                                                "Valor numérico do serviço em reais. Ex: 200.00"),
                                        URGENTE, Map.of("type", "boolean", "description", "Se é um pedido urgente"))))),
                Map.of("type", FUNCTION, FUNCTION, Map.of(
                        "name", "editar_ordem_servico",
                        "description", "Edita uma ordem de serviço existente.",
                        "parameters", Map.of(
                                "type", "object",
                                "required", List.of(OS_ID, "campos"),
                                "properties", Map.of(
                                        OS_ID, Map.of("type", "integer", "description", "ID da ordem de serviço"),
                                        "campos", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "descricao", Map.of("type", "string"),
                                                        "statusServico", Map.of("type", "string"),
                                                        PRAZO_ENTREGA, Map.of("type", "string"),
                                                        VALOR,
                                                        Map.of("type", "number", "description", "Novo valor numérico"),
                                                        URGENTE, Map.of("type", "boolean"))))))),
                Map.of("type", FUNCTION, FUNCTION, Map.of(
                        "name", CADASTRAR_CLIENTE,
                        "description", "Cadastra um novo cliente no sistema.",
                        "parameters", Map.of(
                                "type", "object",
                                "required", List.of("nome", "telefone"),
                                "properties", Map.of(
                                        "nome", Map.of("type", "string", "description", "Nome completo do cliente"),
                                        "telefone", Map.of("type", "string", "description", "Apenas números"),
                                        "medidas",
                                        Map.of("type", "string", "description", "Medidas ou observações gerais"))))));
    }
}