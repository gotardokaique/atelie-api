package com.gestao.api.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.DispositivoPushRequestDTO;
import com.gestao.api.entities.DispositivoPush;
import com.gestao.api.enuns.PlataformaDispositivo;
import com.gestao.api.repositories.DispositivoPushRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class DispositivoPushService {

    private static final Logger log = LoggerFactory.getLogger(DispositivoPushService.class);

    static final Duration RENOVAR_ULTIMO_USO = Duration.ofHours(1);
    static final int LIMITE_POR_MINUTO = 20;

    // Só grava se algo mudou (dono, ativo, plataforma) ou se o último uso tem mais de 1 h.
    // ON CONFLICT cobre duas requisições simultâneas com o mesmo token: nunca 409.
    private static final String UPSERT =
            "INSERT INTO dispositivos_push (dpu_usuario_id, dpu_expo_push_token, dpu_plataforma, dpu_ativo, "
            + "dpu_criado_em, dpu_ultimo_uso_em) VALUES (?1, ?2, ?3, true, ?4, ?4) "
            + "ON CONFLICT (dpu_expo_push_token) DO UPDATE SET "
            + "dpu_usuario_id = EXCLUDED.dpu_usuario_id, dpu_plataforma = EXCLUDED.dpu_plataforma, "
            + "dpu_ativo = true, dpu_ultimo_uso_em = EXCLUDED.dpu_ultimo_uso_em "
            + "WHERE dispositivos_push.dpu_usuario_id <> EXCLUDED.dpu_usuario_id "
            + "OR dispositivos_push.dpu_ativo = false "
            + "OR dispositivos_push.dpu_plataforma <> EXCLUDED.dpu_plataforma "
            + "OR dispositivos_push.dpu_ultimo_uso_em < ?5";

    private record Janela(long minuto, int chamadas) {}

    private final Map<Long, Janela> chamadasPorUsuario = new ConcurrentHashMap<>();

    private final DispositivoPushRepository repository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public DispositivoPushService(DispositivoPushRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Idempotente: token já ativo no mesmo usuário não escreve no banco (retorna false).
     * Token de outro usuário (troca de conta no aparelho) passa para o atual.
     */
    @Transactional
    public boolean registrar(DispositivoPushRequestDTO dto) {
        Long usuarioId = UserContext.getIdUsuario();
        Instant agora = clock.instant();
        String token = dto.token().trim();
        PlataformaDispositivo plataforma = PlataformaDispositivo.valueOf(dto.plataforma().trim().toUpperCase(Locale.ROOT));

        contarChamada(usuarioId, agora);

        Instant limiteUltimoUso = agora.minus(RENOVAR_ULTIMO_USO);
        Optional<DispositivoPush> atual = repository.findByExpoPushToken(token);
        if (atual.isPresent() && jaRegistrado(atual.get(), usuarioId, plataforma, limiteUltimoUso)) {
            return false;
        }

        int linhas = entityManager.createNativeQuery(UPSERT)
                .setParameter(1, usuarioId)
                .setParameter(2, token)
                .setParameter(3, plataforma.name())
                .setParameter(4, agora)
                .setParameter(5, limiteUltimoUso)
                .executeUpdate();
        return linhas > 0;
    }

    /** Logout: só o dono desativa o próprio token. */
    @Transactional
    public void remover(String token) {
        Long usuarioId = UserContext.getIdUsuario();
        repository.findByExpoPushToken(token.trim())
                .filter(d -> usuarioId.equals(d.getUsuario().getId()))
                .ifPresent(d -> {
                    d.setAtivo(false);
                    repository.save(d);
                });
    }

    private static boolean jaRegistrado(DispositivoPush d, Long usuarioId, PlataformaDispositivo plataforma,
            Instant limiteUltimoUso) {
        if (usuarioId.equals(d.getUsuario().getId()) == false) return false;
        if (Boolean.TRUE.equals(d.getAtivo()) == false) return false;
        if (plataforma.equals(d.getPlataforma()) == false) return false;
        return d.getUltimoUsoEm().isBefore(limiteUltimoUso) == false;
    }

    /** Contenção sem erro: responder 4xx alimentaria um cliente que refaz em caso de falha. Só registra. */
    private void contarChamada(Long usuarioId, Instant agora) {
        long minuto = agora.getEpochSecond() / 60;
        Janela janela = chamadasPorUsuario.compute(usuarioId,
                (id, j) -> j == null || j.minuto() != minuto ? new Janela(minuto, 1) : new Janela(minuto, j.chamadas() + 1));
        int chamadas = janela.chamadas();
        if (chamadas > LIMITE_POR_MINUTO && (chamadas - LIMITE_POR_MINUTO) % LIMITE_POR_MINUTO == 1) {
            log.warn("POST /dispositivos acima do limite: usuario={} chamadas_no_minuto={} limite={}",
                    usuarioId, chamadas, LIMITE_POR_MINUTO);
        }
    }

    int chamadasNoMinuto(Long usuarioId) {
        Janela janela = chamadasPorUsuario.get(usuarioId);
        return janela == null ? 0 : janela.chamadas();
    }
}
