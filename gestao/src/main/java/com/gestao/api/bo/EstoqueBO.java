package com.gestao.api.bo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.DAOController;
import com.gen.core.db.exception.NotFoundException;
import com.gen.core.security.exception.BusinessException;
import com.gestao.api.entities.Insumo;
import com.gestao.api.entities.MovimentacaoEstoque;
import com.gestao.api.entities.Servico;
import com.gestao.api.enuns.TipoMovimentacao;
import com.gestao.api.repositories.InsumoRepository;
import com.gestao.api.services.exceptions.EstoqueInsuficienteException;

/**
 * Regras de movimentação de estoque. Toda alteração de saldo passa por aqui e
 * sempre acompanha um registro no ledger {@link MovimentacaoEstoque} — o saldo
 * denormalizado no {@link Insumo} é só cache.
 *
 * Nota de design: diferente do {@code EmailBO} (que acumula estado fluente em
 * campos de instância), este BO é um singleton compartilhado que executa escritas
 * concorrentes de estoque. Por isso os métodos são <b>stateless</b> — recebem todos
 * os argumentos e não guardam estado entre chamadas. A baixa de saldo usa UPDATE
 * atômico com guarda ({@link InsumoRepository#baixarSaldo}) em vez de read-modify-write,
 * o que evita corrida sem lock pessimista.
 *
 * O chamador deve passar o {@link Insumo} já carregado e escopado pelo usuário.
 */
@Component
public class EstoqueBO {

    private final DAOController dao;
    private final InsumoRepository insumoRepository;
    private final Clock clock;

    public EstoqueBO(DAOController dao, InsumoRepository insumoRepository, Clock clock) {
        this.dao = dao;
        this.insumoRepository = insumoRepository;
        this.clock = clock;
    }

    /**
     * ENTRADA (compra). Recalcula o custo médio ponderado e soma ao saldo.
     */
    @Transactional
    public MovimentacaoEstoque entrada(Insumo insumo, BigDecimal quantidade, BigDecimal custoUnitario,
            String observacao) {
        exigirPositivo(quantidade, "Quantidade de entrada deve ser positiva.");
        if (custoUnitario == null || custoUnitario.signum() < 0) {
            throw new BusinessException("Custo unitário de entrada não pode ser negativo.");
        }

        BigDecimal saldoAtual = nz(insumo.getSaldo());
        BigDecimal custoAtual = nz(insumo.getCustoMedio());

        // novoCusto = (saldo*custoMedio + qtd*custoEntrada) / (saldo + qtd)
        BigDecimal novoSaldo = saldoAtual.add(quantidade);
        BigDecimal numerador = saldoAtual.multiply(custoAtual)
                .add(quantidade.multiply(custoUnitario));
        BigDecimal novoCusto = numerador.divide(novoSaldo, 4, RoundingMode.HALF_UP);

        insumo.setSaldo(novoSaldo.setScale(3, RoundingMode.HALF_UP));
        insumo.setCustoMedio(novoCusto);
        dao.update(insumo);

        return registrar(insumo, null, TipoMovimentacao.ENTRADA, quantidade, custoUnitario, observacao);
    }

    /**
     * SAIDA (consumo por serviço). Congela o custo médio do momento e baixa o saldo
     * de forma atômica. Se o saldo for insuficiente, lança
     * {@link EstoqueInsuficienteException} e a transação inteira sofre rollback.
     *
     * @param servico serviço de origem (não nulo em saída por serviço).
     */
    @Transactional
    public MovimentacaoEstoque saida(Insumo insumo, BigDecimal quantidade, Servico servico, String observacao) {
        exigirPositivo(quantidade, "Quantidade de saída deve ser positiva.");

        BigDecimal custoCongelado = nz(insumo.getCustoMedio());

        int linhas = insumoRepository.baixarSaldo(insumo.getId(), quantidade);
        if (linhas == 0) {
            throw new EstoqueInsuficienteException(
                    "Estoque insuficiente de '" + insumo.getDescricao() + "' (saldo "
                            + nz(insumo.getSaldo()).toPlainString() + ", pedido " + quantidade.toPlainString() + ").");
        }

        return registrar(insumo, servico, TipoMovimentacao.SAIDA, quantidade, custoCongelado, observacao);
    }

    /**
     * AJUSTE de inventário. Recebe a quantidade contada e posta a diferença
     * (entrada ou saída) como movimentação AJUSTE, acertando o saldo. Não mexe no
     * custo médio. Se não houver diferença, nada é registrado.
     */
    @Transactional
    public MovimentacaoEstoque ajuste(Insumo insumo, BigDecimal quantidadeContada, String observacao) {
        if (quantidadeContada == null || quantidadeContada.signum() < 0) {
            throw new BusinessException("Quantidade contada não pode ser negativa.");
        }

        BigDecimal saldoAtual = nz(insumo.getSaldo());
        BigDecimal diff = quantidadeContada.subtract(saldoAtual);

        if (diff.signum() == 0) {
            return null;
        }

        insumo.setSaldo(quantidadeContada.setScale(3, RoundingMode.HALF_UP));
        dao.update(insumo);

        return registrar(insumo, null, TipoMovimentacao.AJUSTE, diff.abs(), nz(insumo.getCustoMedio()), observacao);
    }

    /**
     * Estorna (devolve ao saldo) todas as saídas geradas por um determinado serviço.
     * Cria entradas compensatórias no histórico, preservando o custo médio.
     */
    @Transactional
    public void estornarSaidasPorServico(Servico servico) {
        if (servico == null || servico.getId() == null) {
            return;
        }

        try {
            List<MovimentacaoEstoque> saidas = dao.select()
                    .from(MovimentacaoEstoque.class)
                    .join("insumo")
                    .where("servico.id", com.gen.core.db.Condicao.EQUAL, servico.getId())
                    .where("tipo", com.gen.core.db.Condicao.EQUAL, TipoMovimentacao.SAIDA)
                    .list();

            for (MovimentacaoEstoque saida : saidas) {
                Insumo insumo = saida.getInsumo();
                BigDecimal qtde = saida.getQuantidade();
                BigDecimal saldoAtual = nz(insumo.getSaldo());
                
                insumo.setSaldo(saldoAtual.add(qtde).setScale(3, RoundingMode.HALF_UP));
                dao.update(insumo);

                registrar(insumo, servico, TipoMovimentacao.ENTRADA, qtde, nz(insumo.getCustoMedio()), 
                          "Estorno automático do serviço " + servico.getId());
            }
        } catch (NotFoundException e) {
            // Nenhum movimento encontrado, apenas ignora
        }
    }

    private MovimentacaoEstoque registrar(Insumo insumo, Servico servico, TipoMovimentacao tipo,
            BigDecimal quantidade, BigDecimal custoUnitario, String observacao) {
        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setInsumo(insumo);
        mov.setServico(servico);
        mov.setTipo(tipo);
        mov.setQuantidade(quantidade.setScale(3, RoundingMode.HALF_UP));
        mov.setCustoUnitario(custoUnitario);
        mov.setDataMovimentacao(LocalDateTime.now(clock));
        mov.setObservacao(observacao);
        return dao.insert(mov);
    }

    private static void exigirPositivo(BigDecimal valor, String mensagem) {
        if (valor == null || valor.signum() <= 0) {
            throw new BusinessException(mensagem);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
