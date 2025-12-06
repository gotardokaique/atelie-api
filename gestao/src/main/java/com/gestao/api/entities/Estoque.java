package com.gestao.api.entities;

import java.math.BigDecimal;

import com.gen.core.domain.AbstractEntity;
import com.gen.core.domain.AbstractEntity.Tabela;

import jakarta.persistence.Entity;

@Entity
@Tabela(nome = "estoqueItens")

public class Estoque extends AbstractEntity {

	private static final long serialVersionUID = 1L;

	private Long id;

	@Coluna(nullable = false)
	private String nomeItem;


	@Coluna(nullable = false, precision = 10, scale = 2)
	private BigDecimal valorGasto;

	@Coluna(nullable = false)
	private Integer quantidadeComprada;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNomeItem() {
		return nomeItem;
	}

	public void setNomeItem(String nomeItem) {
		this.nomeItem = nomeItem;
	}

	public BigDecimal getValorGasto() {
		return valorGasto;
	}

	public void setValorGasto(BigDecimal valorGasto) {
		this.valorGasto = valorGasto;
	}

	public Integer getQuantidadeComprada() {
		return quantidadeComprada;
	}

	public void setQuantidadeComprada(Integer quantidadeComprada) {
		this.quantidadeComprada = quantidadeComprada;
	}

}