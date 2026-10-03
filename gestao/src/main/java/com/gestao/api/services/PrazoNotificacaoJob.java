package com.gestao.api.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gestao.api.config.RedisLock;
import com.gestao.api.controllers.DTOs.EnvioPushDTO;
import com.gestao.api.controllers.DTOs.SimulacaoNotificacaoDTO;
import com.gestao.api.entities.Notificacao;
import com.gestao.api.enuns.TipoNotificacao;
import com.gestao.api.repositories.DispositivoPushRepository;
import com.gestao.api.services.PrazoNotificacaoDados.UsuarioResumo;
import com.gestao.api.services.PrazoNotificacaoPlanner.Planejada;
import com.gestao.api.services.PrazoNotificacaoPlanner.Preferencias;
import com.gestao.api.services.PrazoNotificacaoPlanner.ServicoPrazo;

/**
 * Roda de hora em hora (UTC) e processa cada usuário uma vez por dia, a partir das 8h
 * no fuso dele. A janela vai até 20h59 para recuperar uma hora perdida (deploy, queda),
 * sem nunca mandar push de madrugada.
 */
@Component
public class PrazoNotificacaoJob {

    private static final Logger log = LoggerFactory.getLogger(PrazoNotificacaoJob.class);

    static final int HORA_INICIO = 8;
    static final int HORA_FIM = 21;
    /** Margem para o atraso máximo (30 dias) somada à diferença de fuso. */
    private static final int DIAS_ATRAS_CARREGADOS = PrazoNotificacaoPlanner.ATRASO_MAXIMO_DIAS + 2;

    public record ResumoExecucao(int usuariosProcessados, int notificacoesCriadas, EnvioPushDTO push) {}

    private final PrazoNotificacaoDados dados;
    private final PrazoNotificacaoProcessor processor;
    private final NotificacaoPushService pushService;
    private final DispositivoPushRepository dispositivoRepository;
    private final RedisLock lock;
    private final Clock clock;
    private final boolean habilitado;

    public PrazoNotificacaoJob(PrazoNotificacaoDados dados, PrazoNotificacaoProcessor processor,
            NotificacaoPushService pushService, DispositivoPushRepository dispositivoRepository, RedisLock lock,
            Clock clock, @Value("${notificacoes.job.enabled:false}") boolean habilitado) {
        this.dados = dados;
        this.processor = processor;
        this.pushService = pushService;
        this.dispositivoRepository = dispositivoRepository;
        this.lock = lock;
        this.clock = clock;
        this.habilitado = habilitado;
        log.info("[PRAZOS] Job de notificações de prazo {}.", habilitado ? "LIGADO" : "desligado (notificacoes.job.enabled=false)");
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void executarAgendado() {
        if (habilitado == false) {
            return;
        }
        Instant agora = clock.instant();
        if (lock.adquirir("prazos:execucao:" + agora.truncatedTo(ChronoUnit.HOURS), Duration.ofMinutes(55)) == false) {
            log.info("[PRAZOS] Outra instância já está executando esta rodada.");
            return;
        }
        executar(agora, true);
    }

    /** {@code respeitarJanela=false} ignora horário e marcador do dia (disparo manual); o dedupe continua valendo. */
    public ResumoExecucao executar(Instant agora, boolean respeitarJanela) {
        Map<Long, List<ServicoPrazo>> servicos = dados.servicosAbertosPorUsuario(entregaDesde(agora));
        Map<Long, Preferencias> preferencias = dados.preferencias(servicos.keySet());

        int processados = 0;
        int criadas = 0;
        EnvioPushDTO push = EnvioPushDTO.vazio();

        for (Map.Entry<Long, List<ServicoPrazo>> entrada : servicos.entrySet()) {
            Long usuarioId = entrada.getKey();
            Preferencias pref = preferencias.get(usuarioId);
            LocalDate hoje = PrazoNotificacaoPlanner.hojeDoUsuario(agora, pref);
            String marcador = "prazos:feito:" + usuarioId + ":" + hoje;

            if (respeitarJanela && (dentroDaJanela(agora, pref) == false || lock.existe(marcador))) {
                continue;
            }

            try {
                List<Notificacao> novas = processor.processar(usuarioId, pref, entrada.getValue(), agora);
                push = push.somar(pushService.enviar(usuarioId, novas));
                criadas += novas.size();
                processados++;
                if (respeitarJanela) {
                    lock.adquirir(marcador, Duration.ofHours(36));
                }
            } catch (Exception e) {
                log.error("[PRAZOS] Falha ao processar usuário {}: {}", usuarioId, e.getMessage(), e);
            }
        }

        log.info("[PRAZOS] Rodada concluída: {} usuário(s), {} notificação(ões) nova(s), push {}.",
                processados, criadas, push);
        return new ResumoExecucao(processados, criadas, push);
    }

    /** Dry-run: o que cada usuário receberia hoje, sem gravar nem enviar. Ignora a janela de horário. */
    public List<SimulacaoNotificacaoDTO> simular(Instant agora) {
        Map<Long, List<ServicoPrazo>> servicos = dados.servicosAbertosPorUsuario(entregaDesde(agora));
        Map<Long, Preferencias> preferencias = dados.preferencias(servicos.keySet());
        Map<Long, UsuarioResumo> usuarios = dados.usuarios(servicos.keySet());
        Map<Long, Long> dispositivos = new HashMap<>();
        if (servicos.isEmpty() == false) {
            dispositivoRepository.contarAtivosPorUsuario(servicos.keySet())
                    .forEach(l -> dispositivos.put((Long) l[0], (Long) l[1]));
        }

        List<SimulacaoNotificacaoDTO> resultado = new ArrayList<>();
        servicos.forEach((usuarioId, lista) -> {
            Preferencias pref = preferencias.get(usuarioId);
            List<Planejada> planejadas = processor.simular(usuarioId, pref, lista, agora);
            if (planejadas.isEmpty()) {
                return;
            }

            Map<TipoNotificacao, Integer> porTipo = new EnumMap<>(TipoNotificacao.class);
            List<String> titulos = new ArrayList<>();
            int existentes = 0;
            for (Planejada p : planejadas) {
                if (processor.jaExiste(p)) {
                    existentes++;
                    continue;
                }
                porTipo.merge(p.tipo(), 1, Integer::sum);
                titulos.add(p.titulo());
            }

            UsuarioResumo u = usuarios.get(usuarioId);
            resultado.add(new SimulacaoNotificacaoDTO(
                    usuarioId,
                    u == null ? null : u.nome(),
                    u == null ? null : u.email(),
                    pref.fuso().getId(),
                    PrazoNotificacaoPlanner.hojeDoUsuario(agora, pref),
                    pref.notificarPrazo(),
                    pref.diasAntecedencia(),
                    dispositivos.getOrDefault(usuarioId, 0L),
                    planejadas.size() - existentes,
                    existentes,
                    porTipo,
                    titulos));
        });
        resultado.sort((a, b) -> Integer.compare(b.novas(), a.novas()));
        return resultado;
    }

    static boolean dentroDaJanela(Instant agora, Preferencias pref) {
        int hora = agora.atZone(pref.fuso()).getHour();
        return hora >= HORA_INICIO && hora < HORA_FIM;
    }

    private static LocalDate entregaDesde(Instant agora) {
        return agora.atZone(ZoneOffset.UTC).toLocalDate().minusDays(DIAS_ATRAS_CARREGADOS);
    }
}
