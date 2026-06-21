package com.gestao.api.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.gen.core.contracts.UserAccount;
import com.gestao.api.enuns.ProviderUsuario;
import com.gestao.api.enuns.RoleEnum;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario implements UserDetails, Serializable, UserAccount {

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

    @CreationTimestamp
    @Column(name = "usu_data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @UpdateTimestamp
    @Column(name = "usu_data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private Set<UsuarioAcesso> acessos = new HashSet<>();

    public Usuario() {}

    public Usuario(String nome, String email, String senha) {
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    // ---------- getters / setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

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

    public LocalDateTime getDataCadastro() { return dataCadastro; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }

    public Set<UsuarioAcesso> getAcessos() { return acessos; }
    public void setAcessos(Set<UsuarioAcesso> acessos) { this.acessos = acessos; }

    // ---------- UserDetails ----------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.acessos.stream()
                .map(a -> new SimpleGrantedAuthority("ROLE_" + a.getPerfil().getCodigo()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() { return senha; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return Boolean.TRUE.equals(ativo); }

    // ---------- UserAccount (gen-core) ----------

    @Override
    public String getPasswordHash() { return senha; }

    @Override
    public void setPasswordHash(String encoded) { this.senha = encoded; }

    @Override
    public Long getUnidadeId() { return null; }

    @Override
    public String getRole() {
        return this.acessos.stream()
                .map(a -> a.getPerfil().getCodigo())
                .findFirst()
                .orElse(null);
    }

    // ---------- equals / hashCode ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Usuario usuario)) return false;
        return Objects.equals(id, usuario.id) && Objects.equals(email, usuario.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }
}