package com.gestao.api.context;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.gestao.api.entities.Usuario;

public class UserContext {

    public static Usuario getUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("Nenhum usuário autenticado.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Usuario) {
            return (Usuario) principal;
        } else {
            throw new IllegalStateException("Usuário autenticado inválido.");
        }
    }

    public static boolean isUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    public static Long getIdUsuario() {
        Usuario usuario = getUsuarioAutenticado();
        return usuario.getId();
    }

    public static String getEmailUsuario() {
        Usuario usuario = getUsuarioAutenticado();
        return usuario.getEmail();
    }
}
