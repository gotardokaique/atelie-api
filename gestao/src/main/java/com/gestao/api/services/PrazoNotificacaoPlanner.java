package com.gestao.api.services;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Component;

import com.gestao.api.enuns.StatusServico;
import com.gestao.api.enuns.TipoNotificacao;

/**
 * Decide quais notificações de prazo um usuário recebe num dia. Não acessa banco nem Clock:
 * o "hoje" vem sempre de {@code agora} no fuso do próprio usuário.
 */
@Component
public class PrazoNotificacaoPlanner {

    public static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");
    public static final int MAXIMO_INDIVIDUAIS_POR_TIPO = 3;
    public static final int ATRASO_MAXIMO_DIAS = 30;
    public static final int ATRASO_INTERVALO_DIAS = 7;

    private static final int LIMITE_TITULO = 120;
    private static final int LIMITE_DESCRICAO = 255;
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    public record Preferencias(boolean notificarPrazo, int diasAntecedencia, ZoneId fuso) {

        public static Preferencias de(Boolean notificarPrazo, Integer diasAntecedencia, String fuso) {
            return new Preferencias(
                    Boolean.FALSE.equals(notificarPrazo) == false,
                    diasAntecedencia == null ? 3 : diasAntecedencia,
                    resolverFuso(fuso));
        }
    }

    public record ServicoPrazo(
            Long id,
            String descricao,
            String cliente,
            LocalDate entrega,
            StatusServico status,
            LocalDate dataFinalizacao) {
    }

    public record Planejada(
            TipoNotificacao tipo,
            String titulo,
            String descricao,
            Long servicoId,
            LocalDate dataReferencia,
            int quantidade,
            String chaveDedupe) {
    }

    public static ZoneId resolverFuso(String fuso) {
        if (fuso == null || fuso.isBlank()) {
            return FUSO_PADRAO;
        }
        try {
            return ZoneId.of(fuso.trim());
        } catch (DateTimeException e) {
            return FUSO_PADRAO;
        }
    }

    public static LocalDate hojeDoUsuario(Instant agora, Preferencias preferencias) {
        return agora.atZone(preferencias.fuso()).toLocalDate();
    }

    public List<Planejada> planejar(Long usuarioId, Preferencias preferencias, List<ServicoPrazo> servicos, Instant agora) {
        if (preferencias.notificarPrazo() == false) {
            return List.of();
        }

        LocalDate hoje = hojeDoUsuario(agora, preferencias);
        Map<TipoNotificacao, List<ServicoPrazo>> porTipo = new EnumMap<>(TipoNotificacao.class);

        for (ServicoPrazo servico : servicos) {
            if (emAberto(servico) == false) {
                continue;
            }
            TipoNotificacao tipo = classificar(servico.entrega(), hoje, preferencias.diasAntecedencia());
            if (tipo != null) {
                porTipo.computeIfAbsent(tipo, t -> new ArrayList<>()).add(servico);
            }
        }

        List<Planejada> planejadas = new ArrayList<>();
        porTipo.forEach((tipo, lista) -> {
            lista.sort(Comparator.comparing(ServicoPrazo::entrega).thenComparing(ServicoPrazo::id));
            if (lista.size() > MAXIMO_INDIVIDUAIS_POR_TIPO) {
                planejadas.add(resumo(usuarioId, tipo, lista, hoje));
            } else {
                lista.forEach(s -> planejadas.add(individual(tipo, s, hoje)));
            }
        });
        return planejadas;
    }

    static boolean emAberto(ServicoPrazo servico) {
        return servico.entrega() != null
                && servico.dataFinalizacao() == null
                && servico.status() != StatusServico.FINALIZADO;
    }

    /**
     * Atrasado: avisa no 1º dia de atraso e depois a cada 7 dias, só até 30 dias
     * (dias 1, 8, 15, 22 e 29 = no máximo 5 avisos por serviço).
     */
    static TipoNotificacao classificar(LocalDate entrega, LocalDate hoje, int diasAntecedencia) {
        long faltam = ChronoUnit.DAYS.between(hoje, entrega);
        if (faltam == 0) {
            return TipoNotificacao.PRAZO_HOJE;
        }
        if (faltam == 1) {
            return TipoNotificacao.PRAZO_AMANHA;
        }
        if (faltam > 1 && faltam == diasAntecedencia) {
            return TipoNotificacao.PRAZO_ANTECEDENCIA;
        }
        if (faltam < 0) {
            long atraso = -faltam;
            if (atraso <= ATRASO_MAXIMO_DIAS && (atraso - 1) % ATRASO_INTERVALO_DIAS == 0) {
                return TipoNotificacao.ATRASADO;
            }
        }
        return null;
    }

    private static Planejada individual(TipoNotificacao tipo, ServicoPrazo s, LocalDate hoje) {
        long faltam = ChronoUnit.DAYS.between(hoje, s.entrega());

        String titulo = switch (tipo) {
            case PRAZO_ANTECEDENCIA -> "Faltam " + faltam + " dias para a entrega";
            case PRAZO_AMANHA -> "Entrega amanhã";
            case PRAZO_HOJE -> "Entrega hoje";
            case ATRASADO -> "Entrega atrasada há " + dias(-faltam);
            case TESTE -> "Teste de notificação";
        };

        StringJoiner descricao = new StringJoiner(" · ");
        descricao.add(nomeDoServico(s));
        if (s.cliente() != null && s.cliente().isBlank() == false) {
            descricao.add(s.cliente().trim());
        }
        if (tipo == TipoNotificacao.PRAZO_ANTECEDENCIA) {
            descricao.add("entrega em " + DIA_MES.format(s.entrega()));
        } else if (tipo == TipoNotificacao.ATRASADO) {
            descricao.add("prevista para " + DIA_MES.format(s.entrega()));
        }

        String chave = tipo == TipoNotificacao.ATRASADO
                ? tipo + ":S" + s.id() + ":" + s.entrega() + ":D" + (-faltam)
                : tipo + ":S" + s.id() + ":" + s.entrega();

        return new Planejada(tipo, limitar(titulo, LIMITE_TITULO), limitar(descricao.toString(), LIMITE_DESCRICAO),
                s.id(), s.entrega(), 1, chave);
    }

    private static Planejada resumo(Long usuarioId, TipoNotificacao tipo, List<ServicoPrazo> lista, LocalDate hoje) {
        int quantidade = lista.size();
        LocalDate entrega = lista.get(0).entrega();

        String titulo = switch (tipo) {
            case PRAZO_ANTECEDENCIA -> quantidade + " serviços vencem em " + dias(ChronoUnit.DAYS.between(hoje, entrega));
            case PRAZO_AMANHA -> quantidade + " serviços vencem amanhã";
            case PRAZO_HOJE -> quantidade + " serviços vencem hoje";
            case ATRASADO -> quantidade + " serviços estão atrasados";
            case TESTE -> quantidade + " notificações de teste";
        };

        LocalDate referencia = tipo == TipoNotificacao.ATRASADO ? hoje : entrega;
        String chave = "RESUMO:" + tipo + ":U" + usuarioId + ":" + hoje;
        return new Planejada(tipo, titulo, "Toque para ver a lista", null, referencia, quantidade, chave);
    }

    private static String nomeDoServico(ServicoPrazo s) {
        if (s.descricao() != null && s.descricao().isBlank() == false) {
            return s.descricao().trim();
        }
        return "Serviço #" + s.id();
    }

    private static String dias(long n) {
        return n + (n == 1 ? " dia" : " dias");
    }

    private static String limitar(String texto, int limite) {
        return texto.length() <= limite ? texto : texto.substring(0, limite - 1) + "…";
    }
}
