package com.gestao.api.entities;

import java.io.Serializable;
import java.time.Instant;

import com.gestao.api.enuns.PlataformaDispositivo;

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
@Table(name = "dispositivos_push")
public class DispositivoPush implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dpu_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpu_usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "dpu_expo_push_token", nullable = false, unique = true, length = 255)
    private String expoPushToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "dpu_plataforma", nullable = false, length = 20)
    private PlataformaDispositivo plataforma;

    @Column(name = "dpu_ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "dpu_criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "dpu_ultimo_uso_em", nullable = false)
    private Instant ultimoUsoEm;

    public DispositivoPush() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getExpoPushToken() { return expoPushToken; }
    public void setExpoPushToken(String expoPushToken) { this.expoPushToken = expoPushToken; }

    public PlataformaDispositivo getPlataforma() { return plataforma; }
    public void setPlataforma(PlataformaDispositivo plataforma) { this.plataforma = plataforma; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }

    public Instant getUltimoUsoEm() { return ultimoUsoEm; }
    public void setUltimoUsoEm(Instant ultimoUsoEm) { this.ultimoUsoEm = ultimoUsoEm; }
}
