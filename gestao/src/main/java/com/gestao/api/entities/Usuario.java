package com.gestao.api.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.gen.core.contracts.UserAccount;
import com.gestao.api.enuns.ProviderUsuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario implements UserAccount, Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usu_nome", nullable = false)
    private String nome;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "usu_senha", nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(name = "usu_provider", nullable = false)
    private ProviderUsuario provider = ProviderUsuario.LOCAL;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "usu_foto", columnDefinition = "TEXT")
    private String foto;

    @Column(name = "usu_ativo", nullable = false)
    private Boolean ativo = true;

    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private List<UsuarioAcesso> acessos = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "usu_data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @UpdateTimestamp
    @Column(name = "usu_data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    public Usuario() {}

    public Usuario(String nome, String email, String senha) {
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    @Override
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public ProviderUsuario getProvider() { return provider; }
    public void setProvider(ProviderUsuario provider) { this.provider = provider; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public List<UsuarioAcesso> getAcessos() { return acessos; }
    public void setAcessos(List<UsuarioAcesso> acessos) { this.acessos = acessos; }

    public LocalDateTime getDataCadastro() { return dataCadastro; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }

    // ---------- UserAccount ----------

    @Override
    public String getPasswordHash() { return senha; }

    @Override
    public void setPasswordHash(String encoded) { this.senha = encoded; }

    @Override
    public Long getUnidadeId() { return null; }

    @Override
    public Collection<String> getRoles() {
        if (acessos == null) return List.of();
        return acessos.stream()
                .map(a -> a.getPerfil().getCodigo())
                .toList();
    }

    @Override
    public boolean isEnabled() { return Boolean.TRUE.equals(ativo); }

    // ---------- equals / hashCode ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Usuario usuario) {
            return Objects.equals(id, usuario.id) && Objects.equals(email, usuario.email);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }
}