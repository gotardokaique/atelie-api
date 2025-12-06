package com.gestao.api.mappers;

import java.util.List;

import org.mapstruct.Mapper;

import com.gestao.api.controllers.DTOs.TipoServicoDTO;
import com.gestao.api.entities.TipoServico;

@Mapper(componentModel = "spring")
public interface TipoServicoMapper {
    TipoServicoDTO toDto(TipoServico tipoServico);
    TipoServico toEntity(TipoServicoDTO tipoServicoDTO);
    List<TipoServicoDTO> toDtoList(List<TipoServico> tiposServico);
    // void updateEntityFromDto(TipoServicoDTO dto, @MappingTarget TipoServico entity);
}