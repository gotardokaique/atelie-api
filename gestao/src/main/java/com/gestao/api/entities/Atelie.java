package com.gestao.api.entities;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Dados do ateliê do usuário (aparecem em recibos, relatórios e
 * futuros comprovantes). Relação 1:1 com {@link Usuario}.
 */
@Entity
@Table(name = "atelies")
public class Atelie implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ate_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ate_usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "ate_nome", length = 120)
    private String nome;

    /** Logo: TEXT aceita URL ou data URL base64. */
    @Column(name = "ate_logo", columnDefinition = "TEXT")
    private String logo;

    @Column(name = "ate_endereco", length = 255)
    private String endereco;

    @Column(name = "ate_cnpj_cpf", length = 20)
    private String cnpjCpf;

    @Column(name = "ate_contato_publico", length = 120)
    private String contatoPublico;

    /** Horário de funcionamento em texto livre (ex.: "Seg-Sex 09:00-18:00"). */
    @Column(name = "ate_horario_funcionamento", length = 255)
    private String horarioFuncionamento;

    public Atelie() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public String getCnpjCpf() { return cnpjCpf; }
    public void setCnpjCpf(String cnpjCpf) { this.cnpjCpf = cnpjCpf; }

    public String getContatoPublico() { return contatoPublico; }
    public void setContatoPublico(String contatoPublico) { this.contatoPublico = contatoPublico; }

    public String getHorarioFuncionamento() { return horarioFuncionamento; }
    public void setHorarioFuncionamento(String horarioFuncionamento) { this.horarioFuncionamento = horarioFuncionamento; }
}
