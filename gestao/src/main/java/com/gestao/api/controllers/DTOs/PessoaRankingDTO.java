package com.gestao.api.controllers.DTOs;

import java.math.BigDecimal;

public class PessoaRankingDTO {

    private Long pessoaId;
    private String nome;
    private long quantidade;
    private BigDecimal total;

    public PessoaRankingDTO() {}

    public PessoaRankingDTO(Long pessoaId, String nome, long quantidade, BigDecimal total) {
        this.pessoaId = pessoaId;
        this.nome = nome;
        this.quantidade = quantidade;
        this.total = total;
    }

    public Long getPessoaId() { return pessoaId; }	
    public void setPessoaId(Long pessoaId) { this.pessoaId = pessoaId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public long getQuantidade() { return quantidade; }
    public void setQuantidade(long quantidade) { this.quantidade = quantidade; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}
