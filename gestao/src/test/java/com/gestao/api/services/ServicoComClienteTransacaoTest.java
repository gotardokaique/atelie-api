package com.gestao.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import com.gen.core.db.DAOController;
import com.gen.core.db.QueryBuilder;
import com.gestao.api.bo.EstoqueBO;
import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.controllers.DTOs.ServicoComClienteRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoComClienteResponseDTO;
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.entities.Usuario;
import com.gestao.api.services.exceptions.TelefoneJaCadastradoException;

import jakarta.persistence.EntityManagerFactory;

/**
 * Sobe só o proxy transacional do Spring em volta dos services reais, com um
 * gerenciador de transação que registra begin/commit/rollback. Prova que cliente
 * e serviço participam da MESMA transação física e que uma falha no serviço
 * desfaz o cliente (rollback, nunca commit).
 */
@SpringJUnitConfig(ServicoComClienteTransacaoTest.Config.class)
class ServicoComClienteTransacaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 3);

    @Configuration
    @EnableTransactionManagement
    static class Config {

        /** Fora do contexto de propósito: como bean, o mock ganharia o proxy transacional do DAOController. */
        static final DAOController DAO = mock(DAOController.class);

        @Bean
        TransacaoGravada transactionManager() {
            return new TransacaoGravada();
        }

        @Bean
        EntityManagerFactory entityManagerFactory() {
            return mock(EntityManagerFactory.class);
        }

        @Bean
        PessoaService pessoaService() {
            return new PessoaService(DAO);
        }

        @Bean
        ServicoService servicoService(PessoaService pessoaService) {
            Clock clock = Clock.fixed(Instant.parse("2026-10-03T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
            return new ServicoService(DAO, clock, mock(DespesaService.class),
                    mock(ProdutoService.class), mock(EstoqueBO.class), pessoaService);
        }
    }

    /** Gerenciador sem banco: só conta o ciclo de vida das transações físicas. */
    static class TransacaoGravada extends AbstractPlatformTransactionManager {

        private static final long serialVersionUID = 1L;

        int inicios;
        int commits;
        int rollbacks;
        private boolean ativa;

        void zerar() {
            inicios = 0;
            commits = 0;
            rollbacks = 0;
            ativa = false;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ativa;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ativa = true;
            inicios++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            // participante falhou: a transação externa decide o rollback.
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ativa = false;
        }
    }

    @Autowired
    private ServicoService servicoService;

    private final DAOController daoController = Config.DAO;

    @Autowired
    private TransacaoGravada transacoes;

    private QueryBuilder consulta;

    @BeforeEach
    void preparar() {
        reset(daoController);
        transacoes.zerar();

        Usuario usuario = new Usuario();
        usuario.setId(7L);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, List.of()));

        consulta = mock(QueryBuilder.class, Answers.RETURNS_SELF);
        when(daoController.select()).thenReturn(consulta);
        when(consulta.list()).thenReturn(List.of());

        when(daoController.insert(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(10L);
            return pessoa;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static ServicoComClienteRequestDTO pedido(LocalDate entrega) {
        PessoaDTO cliente = new PessoaDTO(null, "Maria Souza", "(11) 98888-7777", "Busto 92", null);
        ServicoRequestDTO servico = new ServicoRequestDTO(null, "Barra de calça", entrega,
                new BigDecimal("45.00"), false, null, false);
        return new ServicoComClienteRequestDTO(cliente, servico);
    }

    @Test
    void falhaAoSalvarServicoDesfazOCliente() {
        when(daoController.insert(any(Servico.class))).thenThrow(new RuntimeException("falha no insert do serviço"));

        assertThatThrownBy(() -> servicoService.criarServicoComCliente(pedido(HOJE.plusDays(3))))
                .hasMessage("falha no insert do serviço");

        verify(daoController).insert(any(Pessoa.class));
        assertThat(transacoes.inicios).as("uma transação física só").isEqualTo(1);
        assertThat(transacoes.rollbacks).isEqualTo(1);
        assertThat(transacoes.commits).isZero();
    }

    @Test
    void servicoInvalidoDepoisDoClienteTambemDesfazTudo() {
        assertThatThrownBy(() -> servicoService.criarServicoComCliente(pedido(HOJE.minusDays(1))))
                .hasMessageContaining("passado");

        verify(daoController).insert(any(Pessoa.class));
        verify(daoController, never()).insert(any(Servico.class));
        assertThat(transacoes.rollbacks).isEqualTo(1);
        assertThat(transacoes.commits).isZero();
    }

    @Test
    void telefoneJaCadastradoNaoSalvaNada() {
        when(consulta.list()).thenReturn(List.of(new Pessoa()));

        assertThatThrownBy(() -> servicoService.criarServicoComCliente(pedido(HOJE.plusDays(3))))
                .isInstanceOf(TelefoneJaCadastradoException.class);

        verify(daoController, never()).insert(any());
        assertThat(transacoes.rollbacks).isEqualTo(1);
        assertThat(transacoes.commits).isZero();
    }

    @Test
    void sucessoSalvaClienteEServicoVinculadosNumCommit() throws Exception {
        when(daoController.insert(any(Servico.class))).thenAnswer(inv -> {
            Servico servico = inv.getArgument(0);
            assertThat(servico.getPessoa().getId()).isEqualTo(10L);
            assertThat(servico.getUsuario().getId()).isEqualTo(7L);
            servico.setId(20L);
            return servico;
        });

        ServicoComClienteResponseDTO criado = servicoService.criarServicoComCliente(pedido(HOJE.plusDays(3)));

        assertThat(criado.pessoaId()).isEqualTo(10L);
        assertThat(criado.servicoId()).isEqualTo(20L);
        assertThat(transacoes.inicios).isEqualTo(1);
        assertThat(transacoes.commits).isEqualTo(1);
        assertThat(transacoes.rollbacks).isZero();
    }
}
