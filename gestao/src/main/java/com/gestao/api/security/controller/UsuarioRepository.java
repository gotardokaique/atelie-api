package com.gestao.api.security.controller;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import com.gestao.api.entities.Usuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
	UserDetails findByEmail(String email);

}
