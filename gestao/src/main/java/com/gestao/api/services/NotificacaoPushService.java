package com.gestao.api.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.gestao.api.config.RedisLock;
import com.gestao.api.controllers.DTOs.EnvioPushDTO;
import com.gestao.api.entities.DispositivoPush;
import com.gestao.api.entities.Notificacao;
import com.gestao.api.push.PushMessage;
import com.gestao.api.push.PushReceipt;
import com.gestao.api.push.PushSender;
import com.gestao.api.push.PushTicket;
import com.gestao.api.repositories.DispositivoPushRepository;
import com.gestao.api.repositories.NotificacaoRepository;

/**
 * Envia as notificações já gravadas para os aparelhos do usuário. Falha de push nunca
 * desfaz a notificação: ela continua no sininho.
 */
@Service
public class NotificacaoPushService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoPushService.class);

    public static final String CANAL_PRAZOS = "prazos";
    static final String RECIBOS_PENDENTES = "atelie:push:recibos-pendentes";
    static final Duration ESPERA_RECIBO = Duration.ofMinutes(15);
    static final Duration VALIDADE_RECIBO = Duration.ofHours(24);

    private final PushSender pushSender;
    private final DispositivoPushRepository dispositivoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final StringRedisTemplate redis;
    private final RedisLock lock;
    private final Clock clock;

    public NotificacaoPushService(PushSender pushSender, DispositivoPushRepository dispositivoRepository,
            NotificacaoRepository notificacaoRepository, StringRedisTemplate redis, RedisLock lock, Clock clock) {
        this.pushSender = pushSender;
        this.dispositivoRepository = dispositivoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.redis = redis;
        this.lock = lock;
        this.clock = clock;
    }

    public EnvioPushDTO enviar(Long usuarioId, List<Notificacao> notificacoes) {
        if (notificacoes.isEmpty()) {
            return EnvioPushDTO.vazio();
        }
        try {
            return enviarParaDispositivos(usuarioId, notificacoes);
        } catch (Exception e) {
            log.error("[PUSH] Falha inesperada ao enviar {} notificação(ões) do usuário {}: {}",
                    notificacoes.size(), usuarioId, e.getMessage(), e);
            return new EnvioPushDTO(0, 0, notificacoes.size(), e.getMessage());
        }
    }

    private EnvioPushDTO enviarParaDispositivos(Long usuarioId, List<Notificacao> notificacoes) {
        List<DispositivoPush> dispositivos = dispositivoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        if (dispositivos.isEmpty()) {
            return EnvioPushDTO.vazio();
        }

        List<PushMessage> mensagens = new ArrayList<>();
        List<Long> notificacaoDaMensagem = new ArrayList<>();
        for (Notificacao n : notificacoes) {
            for (DispositivoPush d : dispositivos) {
                mensagens.add(new PushMessage(d.getExpoPushToken(), n.getTitulo(), n.getDescricao(),
                        dadosDaNotificacao(n), CANAL_PRAZOS));
                notificacaoDaMensagem.add(n.getId());
            }
        }

        List<PushTicket> tickets = pushSender.enviar(mensagens);
        Instant agora = clock.instant();
        Set<Long> enviadas = new HashSet<>();
        Set<String> invalidos = new HashSet<>();
        int falhas = 0;
        String erro = null;

        for (int i = 0; i < tickets.size(); i++) {
            PushTicket ticket = tickets.get(i);
            if (ticket.isOk()) {
                enviadas.add(notificacaoDaMensagem.get(i));
                registrarReciboPendente(ticket, agora);
            } else {
                falhas++;
                erro = erro == null ? ticket.erro() : erro;
                if (ticket.isDispositivoInvalido()) {
                    invalidos.add(ticket.token());
                }
            }
        }

        enviadas.forEach(id -> notificacaoRepository.marcarPushEnviado(id, agora));
        if (invalidos.isEmpty() == false) {
            int desativados = dispositivoRepository.desativar(invalidos);
            log.info("[PUSH] {} dispositivo(s) desativado(s) por DeviceNotRegistered no envio.", desativados);
        }

        return new EnvioPushDTO(dispositivos.size(), tickets.size() - falhas, falhas, erro);
    }

    static Map<String, Object> dadosDaNotificacao(Notificacao n) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notificacaoId", n.getId());
        if (n.getServicoId() != null) {
            data.put("servicoId", n.getServicoId());
        }
        data.put("tipo", n.getTipo().name());
        data.put("resumo", n.isResumo());
        if (n.getDataReferencia() != null) {
            data.put("dataReferencia", n.getDataReferencia().toString());
        }
        return data;
    }

    // ===================== RECIBOS =====================

    private void registrarReciboPendente(PushTicket ticket, Instant agora) {
        try {
            redis.opsForHash().put(RECIBOS_PENDENTES, ticket.id(), ticket.token() + "|" + agora.toEpochMilli());
        } catch (Exception e) {
            log.warn("[PUSH] Não foi possível guardar o ticket {} para consultar o recibo: {}", ticket.id(), e.getMessage());
        }
    }

    /** Independe de notificacoes.job.enabled: recibos de pushes de teste também desativam tokens mortos. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
    public void processarRecibos() {
        Instant agora = clock.instant();
        if (lock.adquirir("push:recibos:" + agora.getEpochSecond() / 900, Duration.ofMinutes(14)) == false) {
            return;
        }

        Map<Object, Object> pendentes;
        try {
            pendentes = redis.opsForHash().entries(RECIBOS_PENDENTES);
        } catch (Exception e) {
            log.warn("[PUSH] Redis indisponível ao ler recibos pendentes: {}", e.getMessage());
            return;
        }
        if (pendentes.isEmpty()) {
            return;
        }

        Map<String, String> prontos = new LinkedHashMap<>();
        List<Object> expirados = new ArrayList<>();
        pendentes.forEach((id, valor) -> {
            String[] partes = String.valueOf(valor).split("\\|", 2);
            Instant enviadoEm = partes.length == 2 ? Instant.ofEpochMilli(Long.parseLong(partes[1])) : Instant.EPOCH;
            Duration idade = Duration.between(enviadoEm, agora);
            if (idade.compareTo(VALIDADE_RECIBO) > 0) {
                expirados.add(id);
            } else if (idade.compareTo(ESPERA_RECIBO) >= 0) {
                prontos.put(String.valueOf(id), partes[0]);
            }
        });

        Map<String, PushReceipt> recibos = pushSender.consultarRecibos(prontos.keySet());
        Set<String> invalidos = new HashSet<>();
        List<Object> concluidos = new ArrayList<>(expirados);

        recibos.forEach((id, recibo) -> {
            concluidos.add(id);
            if (recibo.isDispositivoInvalido()) {
                invalidos.add(prontos.get(id));
            } else if (recibo.isOk() == false) {
                log.warn("[PUSH] Recibo {} com erro {}: {}", id, recibo.erro(), recibo.mensagem());
            }
        });

        if (invalidos.isEmpty() == false) {
            int desativados = dispositivoRepository.desativar(invalidos);
            log.info("[PUSH] {} dispositivo(s) desativado(s) por DeviceNotRegistered nos recibos.", desativados);
        }
        if (concluidos.isEmpty() == false) {
            redis.opsForHash().delete(RECIBOS_PENDENTES, concluidos.toArray());
        }
    }
}
