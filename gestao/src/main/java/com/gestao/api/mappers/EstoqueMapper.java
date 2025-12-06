package com.gestao.api.mappers;

import java.util.List;

import org.mapstruct.Mapper;

import com.gestao.api.controllers.DTOs.EstoqueDTO;
import com.gestao.api.entities.Estoque;

@Mapper(componentModel = "spring")
public interface EstoqueMapper {
    EstoqueDTO toDto(Estoque estoque);
    Estoque toEntity(EstoqueDTO estoqueDTO);
    List<EstoqueDTO> toDtoList(List<Estoque> estoques);
    // void updateEntityFromDto(EstoqueDTO dto, @MappingTarget Estoque entity);
}