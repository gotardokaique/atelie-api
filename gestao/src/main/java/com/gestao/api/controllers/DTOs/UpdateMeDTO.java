package com.gestao.api.controllers.DTOs;

/** Atualização do próprio perfil. O front envia sempre nome + foto (foto null = remover). */
public record UpdateMeDTO(String nome, String foto) {}
