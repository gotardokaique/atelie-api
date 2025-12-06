package com.gestao.api.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.gen.core.domain.AbstractEntity;
import com.gen.core.domain.AbstractEntity.Tabela;
import com.gestao.api.enuns.StatusPagamento;
import com.gestao.api.enuns.StatusServico;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Tabela(nome = "servicos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Servico extends AbstractEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pessoa_id", nullable = true)
	private Pessoa pessoa;

//	@NotNull(message = "Tipo de serviço não pode ser nulo")
//	@ManyToOne(fetch = FetchType.LAZY)
//	@JoinColumn(name = "tipo_servico_id", nullable = false)
//	private TipoServico tipoServico;

	@Coluna()
	private String descricao;

	@Coluna()
	private LocalDate dataEntregaPrevista;

	@Coluna(nullable = false, updatable = false)
	private LocalDateTime dataCadastro;

	@Coluna(nullable = true, updatable = true)
	private LocalDate dataFinalizacao;

	@Coluna(nullable = false)
	private boolean urgente = false; 

	@Coluna(precision = 10, scale = 2)
	private BigDecimal valor;

	@Enumerated(EnumType.STRING)
	@Coluna(nullable = false)
	private StatusServico statusServico;

	@Enumerated(EnumType.STRING)
	@Coluna(nullable = false)
	private StatusPagamento statusPagamento;

	public LocalDateTime getDataCadastro() {
		return dataCadastro;
	}

	public void setDataCadastro(LocalDateTime dataCadastro) {
		this.dataCadastro = dataCadastro;
	}

	public LocalDate getDataFinalizacao() {
		return dataFinalizacao;
	}

	public void setDataFinalizacao(LocalDate dataFinalizacao) {
		this.dataFinalizacao = dataFinalizacao;
	}

	public boolean isUrgente() {
		return urgente;
	}

	public void setUrgente(boolean urgente) {
		this.urgente = urgente;
	}

	@PrePersist
	protected void onCreate() {
		this.dataCadastro = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Pessoa getPessoa() {
		return pessoa;
	}

	public void setPessoa(Pessoa pessoa) {
		this.pessoa = pessoa;
	}

//	public TipoServico getTipoServico() {
//		return tipoServico;
//	}
//
//	public void setTipoServico(TipoServico tipoServico) {
//		this.tipoServico = tipoServico;
//	}

	public String getDescricao() {
		return descricao;
	}

	public void setDescricao(String descricao) {
		this.descricao = descricao;
	}

	public LocalDate getDataEntregaPrevista() {
		return dataEntregaPrevista;
	}

	public void setDataEntregaPrevista(LocalDate dataEntregaPrevista) {
		this.dataEntregaPrevista = dataEntregaPrevista;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public void setValor(BigDecimal valor) {
		this.valor = valor;
	}

	public StatusServico getStatusServico() {
		return statusServico;
	}

	public void setStatusServico(StatusServico statusServico) {
		this.statusServico = statusServico;
	}

	public StatusPagamento getStatusPagamento() {
		return statusPagamento;
	}

	public void setStatusPagamento(StatusPagamento statusPagamento) {
		this.statusPagamento = statusPagamento;
	}

	@Override
	public String toString() {
		return "Servico [id=" + id + ", pessoa=" + pessoa + ", descricao=" + descricao + ", dataEntregaPrevista="
				+ dataEntregaPrevista + ", dataCadastro=" + dataCadastro + ", dataFinalizacao=" + dataFinalizacao
				+ ", urgente=" + urgente + ", valor=" + valor + ", statusServico=" + statusServico
				+ ", statusPagamento=" + statusPagamento + "]";
	}
	
	

}