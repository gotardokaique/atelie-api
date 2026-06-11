package com.gestao.api.config.lia.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.gestao.api.config.lia.resolver.ReferenceResolver;
import com.gestao.api.config.lia.tools.LiaFields;
import com.gestao.api.config.lia.tools.LiaTool;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.services.PessoaService;
import com.gestao.api.services.ServicoService;
import com.gestao.api.services.exceptions.BusinessException;

/**
 * Executa as ações de negócio da Lia contra os services existentes.
 * Cada método é coeso e tipado. Resolução de referências já foi feita pelo
 * orquestrador antes de chegar aqui (pessoa_id e os_id já presentes nos writes).
 */
@Service
public class LiaActionService {

    private final PessoaService pessoaService;
    private final ServicoService servicoService;
    private final ReferenceResolver resolver;

    public LiaActionService(PessoaService pessoaService,
                            ServicoService servicoService,
                            ReferenceResolver resolver) {
        this.pessoaService = pessoaService;
        this.servicoService = servicoService;
        this.resolver = resolver;
    }

    public Object execute(LiaTool tool, Map<String, Object> input) {
        return switch (tool) {
            case BUSCAR_CLIENTES -> buscarClientes(input);
            case BUSCAR_ORDENS_SERVICO -> buscarOrdens(input);
            case CRIAR_ORDEM_SERVICO -> criarOrdem(input);
            case EDITAR_ORDEM_SERVICO -> editarOrdem(input);
            case CADASTRAR_CLIENTE -> cadastrarCliente(input);
        };
    }

    // ─── Leitura ──────────────────────────────────────────────────────────

    private List<PessoaResumoDTO> buscarClientes(Map<String, Object> input) {
        String nome = asString(input.get(LiaFields.NOME));
        int limite = asInt(input.get(LiaFields.LIMITE), 10);
        return pessoaService.listarClientesDoUsuario().stream()
                .filter(c -> nome == null || c.nome().toLowerCase().contains(nome.toLowerCase()))
                .limit(limite)
                .toList();
    }

    private List<ServicoResponseDTO> buscarOrdens(Map<String, Object> input) {
        String clienteFiltro = asString(input.get(LiaFields.CLIENTE_NOME));
        String status = asString(input.get(LiaFields.STATUS));
        int limite = asInt(input.get(LiaFields.LIMITE), 10);

        return servicoService.listarServicosEmAberto(null).stream()
                .filter(s -> clienteFiltro == null
                        || s.pessoaNome().toLowerCase().contains(clienteFiltro.toLowerCase()))
                .filter(s -> status == null || s.statusServico().name().equals(status))
                .limit(limite)
                .toList();
    }

    // ─── Escrita ──────────────────────────────────────────────────────────

    private String criarOrdem(Map<String, Object> input) {
        Long pessoaId = asLong(input.get(LiaFields.PESSOA_ID));
        String clienteNome = "";
        clienteNome = asString(input.getOrDefault(LiaFields.CLIENTE_REAL_NOME,
                input.get(LiaFields.CLIENTE_NOME)));

        if (pessoaId == null) {
            PessoaResumoDTO p = resolver.resolverCliente(clienteNome)
                    .orElseThrow(() -> new BusinessException("Cliente não encontrado: "));
            pessoaId = p.id();
            clienteNome = p.nome();
        }

        ServicoRequestDTO request = new ServicoRequestDTO(
                pessoaId,
                asString(input.get(LiaFields.DESCRICAO)),
                parseDate(input.get(LiaFields.PRAZO_ENTREGA)),
                asBigDecimal(input.get(LiaFields.VALOR)),
                asBoolean(input.get(LiaFields.URGENTE)),
                null,
                false);

        servicoService.criarServico(request);
        return "Ordem de serviço criada para " + clienteNome + ".";
    }

    private String editarOrdem(Map<String, Object> input) {
        Long osId = asLong(input.get(LiaFields.OS_ID));
        if (osId == null) {
            throw new BusinessException("Não consegui identificar a ordem de serviço a editar.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> campos = (Map<String, Object>) input.get(LiaFields.CAMPOS);
        if (campos == null) {
            throw new BusinessException("Nenhuma alteração informada.");
        }

        ServicoResponseDTO atual = servicoService.buscarServicoPorId(osId);

        ServicoRequestDTO request = new ServicoRequestDTO(
                atual.pessoaId(),
                campos.containsKey(LiaFields.DESCRICAO)
                        ? asString(campos.get(LiaFields.DESCRICAO))
                        : atual.descricao(),
                campos.containsKey(LiaFields.PRAZO_ENTREGA)
                        ? parseDate(campos.get(LiaFields.PRAZO_ENTREGA))
                        : atual.dataEntregaPrevista(),
                campos.containsKey(LiaFields.VALOR)
                        ? asBigDecimal(campos.get(LiaFields.VALOR))
                        : atual.valor(),
                campos.containsKey(LiaFields.URGENTE)
                        ? asBoolean(campos.get(LiaFields.URGENTE))
                        : atual.urgente(),
                null,
                false);

        if (campos.containsKey(LiaFields.STATUS)) {
            servicoService.atualizarStatusServico(osId,
                    StatusServico.valueOf(asString(campos.get(LiaFields.STATUS))));
        }
        servicoService.atualizarServicoCompleto(osId, request);
        return "Serviço atualizado com sucesso.";
    }

    private String cadastrarCliente(Map<String, Object> input)  {
        String nome = asString(input.get(LiaFields.NOME));
        PessoaDTO dto = new PessoaDTO(
                null,
                nome,
                asString(input.get(LiaFields.TELEFONE)),
                asString(input.get(LiaFields.MEDIDAS)),
                null);
        try {
			pessoaService.criarPessoa(dto);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return "Cliente " + nome + " cadastrado com sucesso!";
    }

    // ─── Coerção de tipos (defensiva, Grok manda String/Number/Boolean) ────

    private String asString(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isBlank() ? null : s;
    }

    private Integer asIntNullable(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Integer.valueOf(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int asInt(Object o, int fallback) {
        Integer v = asIntNullable(o);
        return v == null ? fallback : v;
    }

    private Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal asBigDecimal(Object o) {
        if (o == null || o.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean asBoolean(Object o) {
        return o != null && Boolean.parseBoolean(o.toString());
    }

    private LocalDate parseDate(Object o) {
        String s = asString(o);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Exposto para o orquestrador resolver os_ref → os_id antes do preview. */
    public Optional<ServicoResponseDTO> resolverOs(String osRef) {
        return resolver.resolverOrdemServico(osRef);
    }
}
