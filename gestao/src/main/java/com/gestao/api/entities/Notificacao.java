package com.gestao.api.entities;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

import com.gestao.api.enuns.TipoNotificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notificacoes")
public class Notificacao implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ntf_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ntf_usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "ntf_tipo", nullable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(name = "ntf_titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "ntf_descricao", length = 255)
    private String descricao;

    /** Null no resumo: ele aponta para a lista filtrada. */
    @Column(name = "ntf_servico_id")
    private Long servicoId;

    @Column(name = "ntf_data_referencia")
    private LocalDate dataReferencia;

    @Column(name = "ntf_quantidade", nullable = false)
    private Integer quantidade = 1;

    @Column(name = "ntf_criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "ntf_push_enviado_em")
    private Instant pushEnviadoEm;

    @Column(name = "ntf_visualizada_em")
    private Instant visualizadaEm;

    @Column(name = "ntf_lida_em")
    private Instant lidaEm;

    @Column(name = "ntf_chave_dedupe", nullable = false, unique = true, length = 150)
    private String chaveDedupe;

    public Notificacao() {}

    public boolean isResumo() {
        return servicoId == null && quantidade != null && quantidade > 1;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public TipoNotificacao getTipo() { return tipo; }
    public void setTipo(TipoNotificacao tipo) { this.tipo = tipo; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Long getServicoId() { return servicoId; }
    public void setServicoId(Long servicoId) { this.servicoId = servicoId; }

    public LocalDate getDataReferencia() { return dataReferencia; }
    public void setDataReferencia(LocalDate dataReferencia) { this.dataReferencia = dataReferencia; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public Instant getCriadaEm() { return criadaEm; }
    public void setCriadaEm(Instant criadaEm) { this.criadaEm = criadaEm; }

    public Instant getPushEnviadoEm() { return pushEnviadoEm; }
    public void setPushEnviadoEm(Instant pushEnviadoEm) { this.pushEnviadoEm = pushEnviadoEm; }

    public Instant getVisualizadaEm() { return visualizadaEm; }
    public void setVisualizadaEm(Instant visualizadaEm) { this.visualizadaEm = visualizadaEm; }

    public Instant getLidaEm() { return lidaEm; }
    public void setLidaEm(Instant lidaEm) { this.lidaEm = lidaEm; }

    public String getChaveDedupe() { return chaveDedupe; }
    public void setChaveDedupe(String chaveDedupe) { this.chaveDedupe = chaveDedupe; }
}
