package com.gestao.api.entities;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "limites_mei")
public class LimiteMei implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ano", nullable = false, unique = true)
    private Integer ano;

    @Column(name = "valor_limite", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorLimite;

    @Column(name = "observacao")
    private String observacao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public BigDecimal getValorLimite() { return valorLimite; }
    public void setValorLimite(BigDecimal valorLimite) { this.valorLimite = valorLimite; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
