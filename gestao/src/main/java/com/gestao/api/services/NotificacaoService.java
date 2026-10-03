package com.gestao.api.services;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.PageResult;
import com.gen.core.db.exception.NotFoundException;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.NotificacaoContagemDTO;
import com.gestao.api.controllers.DTOs.NotificacaoDTO;
import com.gestao.api.entities.Notificacao;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.enuns.TipoNotificacao;
import com.gestao.api.repositories.NotificacaoRepository;
import com.gestao.api.services.PrazoNotificacaoPlanner.Planejada;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class NotificacaoService implements NotificacaoStore {

    private static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;

    private static final String INSERT_SE_NOVA =
            "INSERT INTO notificacoes (ntf_usuario_id, ntf_tipo, ntf_titulo, ntf_descricao, ntf_servico_id, "
            + "ntf_data_referencia, ntf_quantidade, ntf_criada_em, ntf_chave_dedupe) "
            + "VALUES (?1, ?2, ?3, ?4, %s, ?5, ?6, now(), ?7) "
            + "ON CONFLICT (ntf_chave_dedupe) DO NOTHING RETURNING ntf_id";

    private final NotificacaoRepository repository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public NotificacaoService(NotificacaoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ===================== DEDUPE =====================

    @Override
    @Transactional
    public Optional<Notificacao> salvarSeNova(Long usuarioId, Planejada p) {
        // ON CONFLICT garante a unicidade mesmo com duas execuções simultâneas.
        String servicoId = p.servicoId() == null ? "NULL" : String.valueOf(p.servicoId().longValue());
        List<?> ids = entityManager.createNativeQuery(String.format(INSERT_SE_NOVA, servicoId))
                .setParameter(1, usuarioId)
                .setParameter(2, p.tipo().name())
                .setParameter(3, p.titulo())
                .setParameter(4, p.descricao())
                .setParameter(5, p.dataReferencia())
                .setParameter(6, p.quantidade())
                .setParameter(7, p.chaveDedupe())
                .getResultList();

        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return repository.findById(((Number) ids.get(0)).longValue());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existe(String chaveDedupe) {
        return repository.existsByChaveDedupe(chaveDedupe);
    }

    // ===================== USUÁRIO LOGADO =====================

    @Transactional(readOnly = true)
    public PageResult<NotificacaoDTO> listar(int page, int size) {
        int pagina = Math.max(page, 0);
        int tamanho = size > 0 ? Math.min(size, TAMANHO_MAXIMO) : TAMANHO_PADRAO;

        Page<Notificacao> resultado = repository.findByUsuarioIdOrderByCriadaEmDescIdDesc(
                UserContext.getIdUsuario(), PageRequest.of(pagina, tamanho));

        return new PageResult<>(
                resultado.getContent().stream().map(NotificacaoDTO::refactor).toList(),
                pagina, tamanho, resultado.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NotificacaoContagemDTO contagem() {
        Long usuarioId = UserContext.getIdUsuario();
        return new NotificacaoContagemDTO(
                repository.contarNaoVisualizadas(usuarioId),
                repository.contarNaoLidas(usuarioId));
    }

    @Transactional
    public NotificacaoContagemDTO visualizarTodas() {
        repository.visualizarTodas(UserContext.getIdUsuario(), clock.instant());
        return contagem();
    }

    @Transactional
    public NotificacaoDTO marcarLida(Long id) {
        Notificacao n = repository.findByIdAndUsuarioId(id, UserContext.getIdUsuario())
                .orElseThrow(() -> new NotFoundException("Notificação não encontrada."));

        Instant agora = clock.instant();
        if (n.getLidaEm() == null) {
            n.setLidaEm(agora);
        }
        if (n.getVisualizadaEm() == null) {
            n.setVisualizadaEm(agora);
        }
        return NotificacaoDTO.refactor(repository.save(n));
    }

    @Transactional
    public NotificacaoContagemDTO lerTodas() {
        repository.lerTodas(UserContext.getIdUsuario(), clock.instant());
        return contagem();
    }

    // ===================== TESTE =====================

    /** Notificação de teste ligada ao serviço aberto mais próximo do prazo, para validar o "tocar e abrir". */
    @Transactional
    public Notificacao criarTeste(Long usuarioId) {
        List<Object[]> proximo = entityManager.createQuery(
                        "SELECT s.id, s.descricao FROM Servico s "
                        + "WHERE s.usuario.id = :usuarioId AND s.statusServico <> :finalizado "
                        + "AND s.dataFinalizacao IS NULL "
                        + "ORDER BY CASE WHEN s.dataEntregaPrevista IS NULL THEN 1 ELSE 0 END, "
                        + "s.dataEntregaPrevista, s.id",
                        Object[].class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("finalizado", StatusServico.FINALIZADO)
                .setMaxResults(1)
                .getResultList();

        Long servicoId = proximo.isEmpty() ? null : (Long) proximo.get(0)[0];
        String descricao = servicoId == null
                ? "Os avisos de prazo estão funcionando."
                : "Toque para abrir: " + nomeServico(servicoId, (String) proximo.get(0)[1]);

        Planejada teste = new Planejada(
                TipoNotificacao.TESTE,
                "Teste de notificação",
                descricao,
                servicoId,
                LocalDate.now(clock),
                1,
                "TESTE:U" + usuarioId + ":" + UUID.randomUUID());

        return salvarSeNova(usuarioId, teste)
                .orElseThrow(() -> new IllegalStateException("Falha ao gravar notificação de teste."));
    }

    private static String nomeServico(Long id, String descricao) {
        return descricao == null || descricao.isBlank() ? "Serviço #" + id : descricao.trim();
    }
}
