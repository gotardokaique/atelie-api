package com.gestao.api.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.ConfiguracaoDTO;
import com.gestao.api.entities.Configuracao;
import com.gestao.api.entities.Usuario;

@Service
public class ConfiguracaoService {

    private final DAOController daoController;

    public ConfiguracaoService(DAOController daoController) {
        this.daoController = daoController;
    }

    // ===================== OBTER (cria com defaults se não existir) =====================

    @Transactional
    public ConfiguracaoDTO obterDoUsuarioLogado() {
        return ConfiguracaoDTO.refactor(obterOuCriar(UserContext.getIdUsuario()));
    }

    // ===================== ATUALIZAR =====================

    @Transactional
    public ConfiguracaoDTO atualizar(ConfiguracaoDTO dto) {
        Configuracao cfg = obterOuCriar(UserContext.getIdUsuario());

        // Telefone aceita string vazia (limpa o valor); só ignora null.
        if (dto.telefone() != null)                   cfg.setTelefone(dto.telefone().isBlank() ? null : dto.telefone().trim());
        if (dto.tema() != null)                       cfg.setTema(dto.tema());
        if (dto.paleta() != null)                     cfg.setPaleta(dto.paleta());
        if (dto.papelParede() != null)                cfg.setPapelParede(dto.papelParede());
        if (dto.densidade() != null)                  cfg.setDensidade(dto.densidade());
        if (dto.idioma() != null)                     cfg.setIdioma(dto.idioma());
        if (dto.formatoData() != null)                cfg.setFormatoData(dto.formatoData());
        if (dto.moeda() != null)                      cfg.setMoeda(dto.moeda());
        if (dto.fusoHorario() != null)                cfg.setFusoHorario(dto.fusoHorario());
        if (dto.paginaInicial() != null)              cfg.setPaginaInicial(dto.paginaInicial());
        if (dto.notificarPrazo() != null)             cfg.setNotificarPrazo(dto.notificarPrazo());
        if (dto.diasAntecedenciaPrazo() != null)      cfg.setDiasAntecedenciaPrazo(dto.diasAntecedenciaPrazo());
        if (dto.notificarPagamentoPendente() != null) cfg.setNotificarPagamentoPendente(dto.notificarPagamentoPendente());
        if (dto.assistenteAtivo() != null)            cfg.setAssistenteAtivo(dto.assistenteAtivo());
        if (dto.assistenteVoz() != null)              cfg.setAssistenteVoz(dto.assistenteVoz());

        return ConfiguracaoDTO.refactor(daoController.update(cfg));
    }

    // ===================== HELPERS =====================

    /**
     * Retorna a configuração do usuário, criando uma linha com os valores default
     * (definidos na entidade {@link Configuracao}) na primeira vez.
     */
    private Configuracao obterOuCriar(Long usuarioId) {
        List<Configuracao> existentes = daoController
                .select()
                .from(Configuracao.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, usuarioId)
                .limit(1)
                .list();

        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }

        Configuracao nova = new Configuracao();
        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(usuarioId);
        nova.setUsuario(usuarioRef);
        return daoController.insert(nova);
    }
}
