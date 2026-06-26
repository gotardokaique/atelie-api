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
