package com.gestao.api.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping; 
import com.gestao.api.controllers.DTOs.ServicoRequestDTO;
import com.gestao.api.controllers.DTOs.ServicoResponseDTO;
import com.gestao.api.entities.Servico;

@Mapper(componentModel = "spring", uses = {PessoaMapper.class, TipoServicoMapper.class})
public interface ServicoMapper {

    ServicoResponseDTO toResponseDto(Servico servico);

    List<ServicoResponseDTO> toResponseDtoList(List<Servico> servicos);

  
    @Mapping(target = "pessoa", ignore = true)
  //  @Mapping(target = "tipoServico", ignore = true)
    @Mapping(target = "id", ignore = true) 
    @Mapping(target = "statusServico", ignore = true) 
    @Mapping(target = "statusPagamento", ignore = true) 
    Servico toEntity(ServicoRequestDTO requestDTO);

}