package com.gestao.api.mappers;

import java.util.List;

import org.mapstruct.Mapper;

import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.entities.Pessoa;

@Mapper(componentModel = "spring")
public interface PessoaMapper {
    PessoaDTO toDto(Pessoa pessoa);
    Pessoa toEntity(PessoaDTO pessoaDTO);
    List<PessoaDTO> toDtoList(List<Pessoa> pessoas);
   
}