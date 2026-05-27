package com.gestao.api.entities;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Liga um produto (catálogo) a um serviço (a venda). Um serviço pode ter N produtos.
 * O {@code precoVendaSnapshot} congela o preço no momento do vínculo, para o total
 * do serviço não mudar se o preço do produto for reajustado depois.
 */
@Entity
@Table(name = "servico_produto")
public class ServicoProduto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade = BigDecimal.ONE;

    @Column(name = "preco_venda_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoVendaSnapshot;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Servico getServico() {
        return servico;
    }

    public void setServico(Servico servico) {
        this.servico = servico;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoVendaSnapshot() {
        return precoVendaSnapshot;
    }

    public void setPrecoVendaSnapshot(BigDecimal precoVendaSnapshot) {
        this.precoVendaSnapshot = precoVendaSnapshot;
    }
}
