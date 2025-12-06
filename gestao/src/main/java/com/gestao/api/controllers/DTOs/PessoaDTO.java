package com.gestao.api.controllers.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record PessoaDTO(
    Long id,

    @NotBlank(message = "Nome não pode ser vazio")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    String nome,


    @NotBlank(message = "Telefone não pode ser vazio")
    String telefone,

    String medidas
) {}