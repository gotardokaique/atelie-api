package com.gestao.api.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.gestao.api.controllers.DTOs.PessoaDTO;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.mappers.PessoaMapper;
import com.gestao.api.repositories.PessoaRepository;
import com.gestao.api.services.exceptions.BusinessException;
import com.gestao.api.services.exceptions.ResourceNotFoundException; 

@Service
public class PessoaService {

	private final PessoaRepository pessoaRepository;
	private final PessoaMapper pessoaMapper;

	public PessoaService(PessoaRepository pessoaRepository, PessoaMapper pessoaMapper) {
		this.pessoaRepository = pessoaRepository;
		this.pessoaMapper = pessoaMapper;
	}

	@Transactional
	public PessoaDTO criarPessoa(PessoaDTO pessoaDTO) {
		String nomeLimpo = null;
		if (pessoaDTO.nome() != null) {
			nomeLimpo = pessoaDTO.nome().trim();
		}

		String telefoneLimpo = null;
		if (pessoaDTO.telefone() != null) {
			telefoneLimpo = pessoaDTO.telefone().replaceAll("[^0-9]", "");
		}

		String medidasLimpas = null;
		if (pessoaDTO.medidas() != null) {
			medidasLimpas = pessoaDTO.medidas().trim();
		}

		if (pessoaDTO.nome() != null && !StringUtils.hasText(nomeLimpo)) {
			throw new BusinessException("O nome da pessoa não pode ser composto apenas por espaços.");
		}

		if (!StringUtils.hasText(telefoneLimpo)) {
			throw new BusinessException("Telefone é obrigatório.");
		}
		if (telefoneLimpo != null && !telefoneLimpo.matches("^[0-9]+$")) {
			throw new BusinessException("Telefone deve conter apenas números.");
		}
		if (telefoneLimpo != null && (telefoneLimpo.length() < 8 || telefoneLimpo.length() > 15)) {
			throw new BusinessException("Telefone deve ter entre 8 e 15 dígitos.");
		}

		PessoaDTO dtoParaMapear = new PessoaDTO(
            null,
            nomeLimpo,
            telefoneLimpo,
            medidasLimpas
        );

		Pessoa pessoa = pessoaMapper.toEntity(dtoParaMapear);

		Pessoa pessoaSalva = pessoaRepository.save(pessoa);

		return pessoaMapper.toDto(pessoaSalva);
	}

	@Transactional(readOnly = true)
	public List<PessoaDTO> listarTodasPessoas() {
		List<Pessoa> pessoas = pessoaRepository.findAll();
		return pessoaMapper.toDtoList(pessoas);
	}

	@Transactional(readOnly = true)
	public Optional<PessoaDTO> buscarPessoaPorId(Long id) {
		return pessoaRepository.findById(id)
            .map(pessoaMapper::toDto);
	}

	@Transactional
	public PessoaDTO atualizarPessoa(Long id, PessoaDTO pessoaDTO) {
		Pessoa pessoaExistente = pessoaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada com id: " + id));

		String nomeLimpo = null;
		if (pessoaDTO.nome() != null) {
			nomeLimpo = pessoaDTO.nome().trim();
		}

		String telefoneLimpo = null;
		if (pessoaDTO.telefone() != null) {
			telefoneLimpo = pessoaDTO.telefone().replaceAll("[^0-9]", "");
		}

		String medidasLimpas = null;
		if (pessoaDTO.medidas() != null) {
			medidasLimpas = pessoaDTO.medidas().trim();
		}

		if (pessoaDTO.nome() != null && !StringUtils.hasText(nomeLimpo)) {
			throw new BusinessException("O nome da pessoa não pode ser composto apenas por espaços.");
		}

		if (!StringUtils.hasText(telefoneLimpo)) {
			throw new BusinessException("Telefone é obrigatório.");
		}
		if (telefoneLimpo != null && !telefoneLimpo.matches("^[0-9]+$")) {
			throw new BusinessException("Telefone deve conter apenas números.");
		}
		if (telefoneLimpo != null && (telefoneLimpo.length() < 8 || telefoneLimpo.length() > 15)) {
			throw new BusinessException("Telefone deve ter entre 8 e 15 dígitos.");
		}


		pessoaExistente.setTelefone(telefoneLimpo);
		pessoaExistente.setMedidas(medidasLimpas);

		Pessoa pessoaAtualizada = pessoaRepository.save(pessoaExistente);
		return pessoaMapper.toDto(pessoaAtualizada);
	}

	@Transactional
	public void deletarPessoa(Long id) {
		if (!pessoaRepository.existsById(id)) {
			throw new ResourceNotFoundException("Pessoa não encontrada com id: " + id);
		}
		pessoaRepository.deleteById(id);
	}
}
