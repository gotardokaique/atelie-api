package com.gestao.api.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity 
@Table(name = "tipos_servico") 
public class TipoServico {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    private Long id;

    @NotBlank(message = "Nome do tipo de serviço não pode ser vazio") 
    @Column(nullable = false, unique = true) 
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    public TipoServico() {}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNome() {
		return nome;
	}

    public void setNome(String nome) {
        this.nome = nome;
    }
    
    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

}
