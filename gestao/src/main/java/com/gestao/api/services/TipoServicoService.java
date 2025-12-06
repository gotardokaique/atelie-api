package com.gestao.api.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.controllers.DTOs.TipoServicoDTO;
import com.gestao.api.entities.TipoServico;
import com.gestao.api.mappers.TipoServicoMapper;
import com.gestao.api.repositories.TipoServicoRepository;

@Service
public class TipoServicoService {

    private final TipoServicoRepository tipoServicoRepository;
    private final TipoServicoMapper tipoServicoMapper;

    public TipoServicoService(TipoServicoRepository tipoServicoRepository, TipoServicoMapper tipoServicoMapper) {
        this.tipoServicoRepository = tipoServicoRepository;
        this.tipoServicoMapper = tipoServicoMapper;
    }

    @Transactional
    public TipoServicoDTO criarTipoServico(TipoServicoDTO tipoServicoDTO) {
        TipoServico tipoServico = tipoServicoMapper.toEntity(tipoServicoDTO);
        TipoServico tipoServicoSalvo = tipoServicoRepository.save(tipoServico);
        return tipoServicoMapper.toDto(tipoServicoSalvo);
    }

    @Transactional(readOnly = true)
    public List<TipoServicoDTO> listarTodosTiposServico() {
        List<TipoServico> tiposServico = tipoServicoRepository.findAll();
        return tipoServicoMapper.toDtoList(tiposServico);
    }

    @Transactional(readOnly = true)
    public Optional<TipoServicoDTO> buscarTipoServicoPorId(Long id) {
        return tipoServicoRepository.findById(id)
                                    .map(tipoServicoMapper::toDto);
    }

    @Transactional
    public TipoServicoDTO atualizarTipoServico(Long id, TipoServicoDTO tipoServicoDTO) {
        TipoServico tipoExistente = tipoServicoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tipo de Serviço não encontrado com id: " + id));

        tipoExistente.setNome(tipoServicoDTO.nome());

        TipoServico tipoAtualizado = tipoServicoRepository.save(tipoExistente);
        return tipoServicoMapper.toDto(tipoAtualizado);
    }

    @Transactional
    public void deletarTipoServico(Long id) {
         if (!tipoServicoRepository.existsById(id)) {
            throw new RuntimeException("Tipo de Serviço não encontrado com id: " + id);
         }
         tipoServicoRepository.deleteById(id);
    }
}