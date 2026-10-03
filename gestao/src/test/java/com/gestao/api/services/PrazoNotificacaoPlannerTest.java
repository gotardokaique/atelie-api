package com.gestao.api.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.gestao.api.entities.Notificacao;
import com.gestao.api.enuns.StatusServico;
import com.gestao.api.enuns.TipoNotificacao;
import com.gestao.api.services.PrazoNotificacaoPlanner.Planejada;
import com.gestao.api.services.PrazoNotificacaoPlanner.Preferencias;
import com.gestao.api.services.PrazoNotificacaoPlanner.ServicoPrazo;

class PrazoNotificacaoPlannerTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final ZoneId PORTO_VELHO = ZoneId.of("America/Porto_Velho");
    private static final Long USUARIO = 7L;
    /** 2026-10-02 12:00 em São Paulo. */
    private static final Instant MEIO_DIA_SP = Instant.parse("2026-10-02T15:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 2);

    private final PrazoNotificacaoPlanner planner = new PrazoNotificacaoPlanner();

    private static Preferencias pref(int antecedencia) {
        return new Preferencias(true, antecedencia, SAO_PAULO);
    }

    private static ServicoPrazo servico(long id, LocalDate entrega) {
        return new ServicoPrazo(id, "Vestido de festa", "Maria Souza", entrega, StatusServico.EM_ANDAMENTO, null);
    }

    private List<Planejada> planejar(Preferencias pref, ServicoPrazo... servicos) {
        return planner.planejar(USUARIO, pref, Arrays.asList(servicos), MEIO_DIA_SP);
    }

    @Nested
    class Configuracoes {

        @Test
        void alertaDesligadoNaoGeraNada() {
            Preferencias desligado = new Preferencias(false, 3, SAO_PAULO);
            List<Planejada> r = planejar(desligado, servico(1, HOJE), servico(2, HOJE.plusDays(1)), servico(3, HOJE.minusDays(1)));
            assertThat(r).isEmpty();
        }

        @Test
        void semConfiguracaoUsaPadroes() {
            Preferencias padrao = Preferencias.de(null, null, null);
            assertThat(padrao.notificarPrazo()).isTrue();
            assertThat(padrao.diasAntecedencia()).isEqualTo(3);
            assertThat(padrao.fuso()).isEqualTo(SAO_PAULO);
        }

        @Test
        void antecedenciaAvisaSoNoDiaConfigurado() {
            List<Planejada> r = planejar(pref(5),
                    servico(1, HOJE.plusDays(5)),
                    servico(2, HOJE.plusDays(4)),
                    servico(3, HOJE.plusDays(3)),
                    servico(4, HOJE.plusDays(6)));

            assertThat(r).extracting(Planejada::servicoId).containsExactly(1L);
            assertThat(r.get(0).tipo()).isEqualTo(TipoNotificacao.PRAZO_ANTECEDENCIA);
            assertThat(r.get(0).titulo()).isEqualTo("Faltam 5 dias para a entrega");
        }

        @Test
        void antecedenciaDeUmDiaViraSoAvisoDeAmanha() {
            List<Planejada> r = planejar(pref(1), servico(1, HOJE.plusDays(1)));
            assertThat(r).extracting(Planejada::tipo).containsExactly(TipoNotificacao.PRAZO_AMANHA);
        }

        @Test
        void textosDeAntecedenciaAmanhaEHoje() {
            List<Planejada> r = planejar(pref(3),
                    servico(1, LocalDate.of(2026, 10, 5)),
                    servico(2, HOJE.plusDays(1)),
                    servico(3, HOJE));

            Map<TipoNotificacao, Planejada> porTipo = new HashMap<>();
            r.forEach(p -> porTipo.put(p.tipo(), p));

            Planejada antecedencia = porTipo.get(TipoNotificacao.PRAZO_ANTECEDENCIA);
            assertThat(antecedencia.titulo()).isEqualTo("Faltam 3 dias para a entrega");
            assertThat(antecedencia.descricao()).isEqualTo("Vestido de festa · Maria Souza · entrega em 05/10");
            assertThat(porTipo.get(TipoNotificacao.PRAZO_AMANHA).titulo()).isEqualTo("Entrega amanhã");
            assertThat(porTipo.get(TipoNotificacao.PRAZO_HOJE).titulo()).isEqualTo("Entrega hoje");
        }
    }

    @Nested
    class ServicosIgnorados {

        @Test
        void finalizadoNaoGeraAviso() {
            ServicoPrazo finalizado = new ServicoPrazo(1L, "Bainha", "Ana", HOJE, StatusServico.FINALIZADO, null);
            assertThat(planejar(pref(3), finalizado)).isEmpty();
        }

        @Test
        void comDataDeFinalizacaoNaoGeraAvisoMesmoComStatusAberto() {
            ServicoPrazo entregue = new ServicoPrazo(1L, "Bainha", "Ana", HOJE, StatusServico.PENDENTE, HOJE.minusDays(1));
            assertThat(planejar(pref(3), entregue)).isEmpty();
        }

        @Test
        void semDataDeEntregaNaoGeraAviso() {
            assertThat(planejar(pref(3), servico(1, null))).isEmpty();
        }
    }

    @Nested
    class Agrupamento {

        @Test
        void ateTresDoMesmoTipoSaoIndividuais() {
            List<Planejada> r = planejar(pref(3),
                    servico(1, HOJE.plusDays(1)), servico(2, HOJE.plusDays(1)), servico(3, HOJE.plusDays(1)));

            assertThat(r).hasSize(3).allMatch(p -> p.quantidade() == 1 && p.servicoId() != null);
        }

        @Test
        void acimaDeTresViraUmResumoSemServico() {
            List<Planejada> r = planejar(pref(3),
                    servico(1, HOJE.plusDays(1)), servico(2, HOJE.plusDays(1)),
                    servico(3, HOJE.plusDays(1)), servico(4, HOJE.plusDays(1)));

            assertThat(r).hasSize(1);
            Planejada resumo = r.get(0);
            assertThat(resumo.tipo()).isEqualTo(TipoNotificacao.PRAZO_AMANHA);
            assertThat(resumo.quantidade()).isEqualTo(4);
            assertThat(resumo.servicoId()).isNull();
            assertThat(resumo.titulo()).isEqualTo("4 serviços vencem amanhã");
            assertThat(resumo.descricao()).isEqualTo("Toque para ver a lista");
            assertThat(resumo.dataReferencia()).isEqualTo(HOJE.plusDays(1));
        }

        @Test
        void agrupamentoEPorTipo() {
            List<Planejada> r = planejar(pref(3),
                    servico(1, HOJE), servico(2, HOJE), servico(3, HOJE), servico(4, HOJE),
                    servico(5, HOJE.plusDays(1)), servico(6, HOJE.plusDays(1)));

            assertThat(r).filteredOn(p -> p.tipo() == TipoNotificacao.PRAZO_HOJE)
                    .singleElement().satisfies(p -> assertThat(p.quantidade()).isEqualTo(4));
            assertThat(r).filteredOn(p -> p.tipo() == TipoNotificacao.PRAZO_AMANHA).hasSize(2);
        }

        @Test
        void resumoDeAtrasadosUsaODiaComoReferencia() {
            LocalDate entrega = HOJE.minusDays(1);
            List<Planejada> r = planejar(pref(3),
                    servico(1, entrega), servico(2, entrega), servico(3, entrega), servico(4, entrega));

            assertThat(r).singleElement().satisfies(p -> {
                assertThat(p.tipo()).isEqualTo(TipoNotificacao.ATRASADO);
                assertThat(p.titulo()).isEqualTo("4 serviços estão atrasados");
                assertThat(p.dataReferencia()).isEqualTo(HOJE);
            });
        }
    }

    @Nested
    class Atrasados {

        @Test
        void textoDoPrimeiroDiaDeAtraso() {
            List<Planejada> r = planejar(pref(3), servico(1, HOJE.minusDays(1)));
            assertThat(r).singleElement().satisfies(p -> {
                assertThat(p.tipo()).isEqualTo(TipoNotificacao.ATRASADO);
                assertThat(p.titulo()).isEqualTo("Entrega atrasada há 1 dia");
                assertThat(p.descricao()).isEqualTo("Vestido de festa · Maria Souza · prevista para 01/10");
            });
        }

        @Test
        void avisaNoPrimeiroDiaEDepoisACadaSeteDias() {
            LocalDate entrega = LocalDate.of(2026, 1, 10);
            List<Integer> diasComAviso = IntStream.rangeClosed(0, 60)
                    .filter(atraso -> PrazoNotificacaoPlanner.classificar(entrega, entrega.plusDays(atraso), 3)
                            == TipoNotificacao.ATRASADO)
                    .boxed().toList();

            assertThat(diasComAviso).containsExactly(1, 8, 15, 22, 29);
        }

        /** Produção tem serviços abertos desde 2024: o teto evita avisos semanais eternos. */
        @Test
        void tetoDeTrintaDiasLimitaACincoAvisosMesmoRodandoTodoDiaPorUmAno() {
            LocalDate entrega = LocalDate.of(2024, 3, 1);
            ServicoPrazo antigo = servico(99, entrega);
            InMemoryStore store = new InMemoryStore();
            PrazoNotificacaoProcessor processor = new PrazoNotificacaoProcessor(planner, store);

            for (int dia = 0; dia <= 400; dia++) {
                Instant meioDia = entrega.plusDays(dia).atTime(12, 0).atZone(SAO_PAULO).toInstant();
                processor.processar(USUARIO, pref(3), List.of(antigo), meioDia);
            }

            assertThat(store.salvas).filteredOn(n -> n.getTipo() == TipoNotificacao.ATRASADO).hasSize(5);
        }

        @Test
        void servicoAtrasadoHaMaisDeTrintaDiasNaoGeraNada() {
            assertThat(planejar(pref(3), servico(1, HOJE.minusDays(31)), servico(2, HOJE.minusDays(400)))).isEmpty();
        }
    }

    @Nested
    class Dedupe {

        @Test
        void mesmaEntradaGeraAsMesmasChaves() {
            ServicoPrazo[] servicos = { servico(1, HOJE), servico(2, HOJE.plusDays(1)), servico(3, HOJE.minusDays(8)) };
            assertThat(planejar(pref(3), servicos)).extracting(Planejada::chaveDedupe)
                    .containsExactlyElementsOf(planejar(pref(3), servicos).stream().map(Planejada::chaveDedupe).toList());
        }

        @Test
        void rodarDuasVezesNoMesmoDiaNaoDuplica() {
            InMemoryStore store = new InMemoryStore();
            PrazoNotificacaoProcessor processor = new PrazoNotificacaoProcessor(planner, store);
            List<ServicoPrazo> servicos = List.of(servico(1, HOJE), servico(2, HOJE.plusDays(1)), servico(3, HOJE.plusDays(3)));

            List<Notificacao> primeira = processor.processar(USUARIO, pref(3), servicos, MEIO_DIA_SP);
            List<Notificacao> segunda = processor.processar(USUARIO, pref(3), servicos, MEIO_DIA_SP.plusSeconds(3600));

            assertThat(primeira).hasSize(3);
            assertThat(segunda).isEmpty();
            assertThat(store.salvas).hasSize(3);
        }

        @Test
        void resumoTambemNaoDuplica() {
            InMemoryStore store = new InMemoryStore();
            PrazoNotificacaoProcessor processor = new PrazoNotificacaoProcessor(planner, store);
            List<ServicoPrazo> servicos = List.of(
                    servico(1, HOJE), servico(2, HOJE), servico(3, HOJE), servico(4, HOJE));

            processor.processar(USUARIO, pref(3), servicos, MEIO_DIA_SP);
            processor.processar(USUARIO, pref(3), servicos, MEIO_DIA_SP);

            assertThat(store.salvas).hasSize(1);
        }

        @Test
        void mudarADataDeEntregaPermiteNovoAviso() {
            String antes = planejar(pref(3), servico(1, HOJE)).get(0).chaveDedupe();
            String depois = planner.planejar(USUARIO, pref(3), List.of(servico(1, HOJE.plusDays(1))), MEIO_DIA_SP)
                    .get(0).chaveDedupe();
            assertThat(antes).isNotEqualTo(depois);
        }
    }

    @Nested
    class Fuso {

        /** 03:30 UTC = 00:30 em São Paulo (dia 3) e 23:30 em Porto Velho (dia 2). */
        private static final Instant VIRADA = Instant.parse("2026-10-03T03:30:00Z");
        private static final LocalDate DIA_3 = LocalDate.of(2026, 10, 3);

        @Test
        void hojeVemDoFusoDoUsuario() {
            ServicoPrazo entregaDia3 = servico(1, DIA_3);

            List<Planejada> saoPaulo = planner.planejar(USUARIO, new Preferencias(true, 3, SAO_PAULO), List.of(entregaDia3), VIRADA);
            List<Planejada> portoVelho = planner.planejar(USUARIO, new Preferencias(true, 3, PORTO_VELHO), List.of(entregaDia3), VIRADA);

            assertThat(saoPaulo).extracting(Planejada::tipo).containsExactly(TipoNotificacao.PRAZO_HOJE);
            assertThat(portoVelho).extracting(Planejada::tipo).containsExactly(TipoNotificacao.PRAZO_AMANHA);
        }

        @Test
        void naoUsaOFusoPadraoDaJvmNemOClockGlobal() {
            TimeZone original = TimeZone.getDefault();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(PORTO_VELHO));
                List<Planejada> r = planner.planejar(USUARIO, new Preferencias(true, 3, SAO_PAULO), List.of(servico(1, DIA_3)), VIRADA);
                assertThat(r).extracting(Planejada::tipo).containsExactly(TipoNotificacao.PRAZO_HOJE);
            } finally {
                TimeZone.setDefault(original);
            }

            for (Field campo : PrazoNotificacaoPlanner.class.getDeclaredFields()) {
                assertThat(campo.getType()).isNotEqualTo(Clock.class);
            }
        }

        @Test
        void fusoAusenteOuInvalidoCaiEmSaoPaulo() {
            assertThat(PrazoNotificacaoPlanner.resolverFuso(null)).isEqualTo(SAO_PAULO);
            assertThat(PrazoNotificacaoPlanner.resolverFuso("  ")).isEqualTo(SAO_PAULO);
            assertThat(PrazoNotificacaoPlanner.resolverFuso("Marte/Olympus")).isEqualTo(SAO_PAULO);
            assertThat(PrazoNotificacaoPlanner.resolverFuso("America/Manaus")).isEqualTo(ZoneId.of("America/Manaus"));
        }

        @Test
        void janelaDoJobComecaAsOitoNoFusoDoUsuario() {
            Preferencias sp = new Preferencias(true, 3, SAO_PAULO);
            assertThat(PrazoNotificacaoJob.dentroDaJanela(Instant.parse("2026-10-02T10:59:00Z"), sp)).isFalse();
            assertThat(PrazoNotificacaoJob.dentroDaJanela(Instant.parse("2026-10-02T11:00:00Z"), sp)).isTrue();
            assertThat(PrazoNotificacaoJob.dentroDaJanela(Instant.parse("2026-10-02T23:59:00Z"), sp)).isTrue();
            assertThat(PrazoNotificacaoJob.dentroDaJanela(Instant.parse("2026-10-03T00:00:00Z"), sp)).isFalse();

            Preferencias pv = new Preferencias(true, 3, PORTO_VELHO);
            assertThat(PrazoNotificacaoJob.dentroDaJanela(Instant.parse("2026-10-02T11:00:00Z"), pv)).isFalse();
            assertThat(PrazoNotificacaoJob.dentroDaJanela(Instant.parse("2026-10-02T12:00:00Z"), pv)).isTrue();
        }
    }

    /** Mesma semântica do ON CONFLICT DO NOTHING do banco. */
    static class InMemoryStore implements NotificacaoStore {

        final List<Notificacao> salvas = new ArrayList<>();
        private final Map<String, Notificacao> porChave = new HashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<Notificacao> salvarSeNova(Long usuarioId, Planejada p) {
            if (porChave.containsKey(p.chaveDedupe())) {
                return Optional.empty();
            }
            Notificacao n = new Notificacao();
            n.setId(ids.incrementAndGet());
            n.setTipo(p.tipo());
            n.setTitulo(p.titulo());
            n.setServicoId(p.servicoId());
            n.setQuantidade(p.quantidade());
            n.setChaveDedupe(p.chaveDedupe());
            porChave.put(p.chaveDedupe(), n);
            salvas.add(n);
            return Optional.of(n);
        }

        @Override
        public boolean existe(String chaveDedupe) {
            return porChave.containsKey(chaveDedupe);
        }
    }
}
