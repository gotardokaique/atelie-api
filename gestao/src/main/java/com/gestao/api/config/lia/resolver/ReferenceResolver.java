package com.gestao.api.config.lia.resolver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.gestao.api.controllers.DTOs.PessoaResumoDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.services.PessoaService;
import com.gestao.api.services.ServicoService;

/**
 * Resolve referências TEXTUAIS (nome do cliente, "vestido da Ana") para IDs
 * reais — de forma determinística, antes do preview.
 *
 * Conserta o bug do código antigo: editar_ordem_servico exigia os_id que o
 * modelo nunca tinha de onde tirar. Agora o modelo manda os_ref ("vestido da
 * Ana") e o Java encontra a OS. O usuário nunca toca em ID.
 */
@Component
public class ReferenceResolver {

    private final PessoaService pessoaService;
    private final ServicoService servicoService;

    public ReferenceResolver(PessoaService pessoaService, ServicoService servicoService) {
        this.pessoaService = pessoaService;
        this.servicoService = servicoService;
    }

    /** Cliente por nome: tenta match exato, depois "contém". */
    public Optional<PessoaResumoDTO> resolverCliente(String nomeBusca) {
        if (nomeBusca == null || nomeBusca.isBlank()) {
            return Optional.empty();
        }
        String alvo = normalizar(nomeBusca);
        List<PessoaResumoDTO> clientes = pessoaService.listarClientesDoUsuario();

        Optional<PessoaResumoDTO> exato = clientes.stream()
                .filter(c -> normalizar(c.nome()).equals(alvo))
                .findFirst();
        if (exato.isPresent()) {
            return exato;
        }
        return clientes.stream()
                .filter(c -> normalizar(c.nome()).contains(alvo))
                .findFirst();
    }

    /**
     * OS por referência textual livre ("vestido da Ana", "bainha da Maria").
     * Pontua cada OS em aberto por quantos tokens da referência batem com
     * nome do cliente + descrição. Retorna a de maior pontuação (> 0).
     */
    public Optional<ServicoResponseDTO> resolverOrdemServico(String osRef) {
        if (osRef == null || osRef.isBlank()) {
            return Optional.empty();
        }
        String[] tokens = normalizar(osRef).split("\\s+");
        List<ServicoResponseDTO> abertas = servicoService.listarServicosEmAberto(null);

        ServicoResponseDTO melhor = null;
        int melhorScore = 0;

        for (ServicoResponseDTO os : abertas) {
            String alvo = normalizar(os.pessoaNome() + " " + safe(os.descricao()));
            int score = 0;
            for (String token : tokens) {
                if (token.length() >= 3 && alvo.contains(token)) {
                    score++;
                }
            }
            if (score > melhorScore) {
                melhorScore = score;
                melhor = os;
            }
        }
        return Optional.ofNullable(melhor);
    }

    private String normalizar(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
