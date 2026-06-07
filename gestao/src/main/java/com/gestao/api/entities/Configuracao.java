package com.gestao.api.entities;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Preferências do usuário no Gestão Ateliê. Relação 1:1 com {@link Usuario}:
 * cada usuário tem uma única linha de configuração, criada com os valores
 * default na primeira vez que é solicitada.
 */
@Entity
@Table(name = "configuracoes")
public class Configuracao implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cfg_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cfg_usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    // ===================== Perfil =====================

    @Column(name = "cfg_telefone", length = 30)
    private String telefone;

    // ===================== Aparência =====================

    /** Tema base: "light" | "dark" | "system". */
    @Column(name = "cfg_tema", nullable = false)
    private String tema = "system";

    /** Paleta de cores extra (ex.: "default", "rosa", "lavanda", "verde", "azul"). */
    @Column(name = "cfg_paleta", nullable = false)
    private String paleta = "default";

    /** Papel de parede. Por enquanto somente "default". */
    @Column(name = "cfg_papel_parede", nullable = false)
    private String papelParede = "default";

    /** Densidade da interface: "confortavel" | "compacto". */
    @Column(name = "cfg_densidade", nullable = false)
    private String densidade = "confortavel";

    // ===================== Idioma / Formato =====================

    /** Idioma/locale (ex.: "pt-BR"). */
    @Column(name = "cfg_idioma", nullable = false)
    private String idioma = "pt-BR";

    /** Formato de data (ex.: "dd/MM/yyyy"). */
    @Column(name = "cfg_formato_data", nullable = false)
    private String formatoData = "dd/MM/yyyy";

    /** Moeda padrão (ex.: "BRL"). */
    @Column(name = "cfg_moeda", nullable = false)
    private String moeda = "BRL";

    /** Fuso horário IANA (ex.: "America/Sao_Paulo"). */
    @Column(name = "cfg_fuso_horario", nullable = false)
    private String fusoHorario = "America/Sao_Paulo";

    /** Página inicial padrão ao abrir o app (ex.: "home", "servicos"). */
    @Column(name = "cfg_pagina_inicial", nullable = false)
    private String paginaInicial = "home";

    // ===================== Notificações =====================

    /** Alertar sobre prazos de serviços próximos do vencimento. */
    @Column(name = "cfg_notificar_prazo", nullable = false)
    private Boolean notificarPrazo = true;

    /** Quantos dias antes do prazo começar a alertar. */
    @Column(name = "cfg_dias_antecedencia_prazo", nullable = false)
    private Integer diasAntecedenciaPrazo = 3;

    /** Alertar sobre pagamentos pendentes. */
    @Column(name = "cfg_notificar_pagamento", nullable = false)
    private Boolean notificarPagamentoPendente = true;

    // ===================== Assistente =====================

    /** Habilita a assistente (Lia). Default FALSE — opt-in. */
    @Column(name = "cfg_assistente_ativo", nullable = false)
    private Boolean assistenteAtivo = false;

    /** Voice ID ElevenLabs (default Bella). */
    @Column(name = "cfg_assistente_voz", nullable = false)
    private String assistenteVoz = "EXAVITQu4vr4xnSDxMaL";

    public Configuracao() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getTema() { return tema; }
    public void setTema(String tema) { this.tema = tema; }

    public String getPaleta() { return paleta; }
    public void setPaleta(String paleta) { this.paleta = paleta; }

    public String getPapelParede() { return papelParede; }
    public void setPapelParede(String papelParede) { this.papelParede = papelParede; }

    public String getDensidade() { return densidade; }
    public void setDensidade(String densidade) { this.densidade = densidade; }

    public String getIdioma() { return idioma; }
    public void setIdioma(String idioma) { this.idioma = idioma; }

    public String getFormatoData() { return formatoData; }
    public void setFormatoData(String formatoData) { this.formatoData = formatoData; }

    public String getMoeda() { return moeda; }
    public void setMoeda(String moeda) { this.moeda = moeda; }

    public String getFusoHorario() { return fusoHorario; }
    public void setFusoHorario(String fusoHorario) { this.fusoHorario = fusoHorario; }

    public String getPaginaInicial() { return paginaInicial; }
    public void setPaginaInicial(String paginaInicial) { this.paginaInicial = paginaInicial; }

    public Boolean getNotificarPrazo() { return notificarPrazo; }
    public void setNotificarPrazo(Boolean notificarPrazo) { this.notificarPrazo = notificarPrazo; }

    public Integer getDiasAntecedenciaPrazo() { return diasAntecedenciaPrazo; }
    public void setDiasAntecedenciaPrazo(Integer diasAntecedenciaPrazo) { this.diasAntecedenciaPrazo = diasAntecedenciaPrazo; }

    public Boolean getNotificarPagamentoPendente() { return notificarPagamentoPendente; }
    public void setNotificarPagamentoPendente(Boolean notificarPagamentoPendente) { this.notificarPagamentoPendente = notificarPagamentoPendente; }

    public Boolean getAssistenteAtivo() { return assistenteAtivo; }
    public void setAssistenteAtivo(Boolean assistenteAtivo) { this.assistenteAtivo = assistenteAtivo; }

    public String getAssistenteVoz() { return assistenteVoz; }
    public void setAssistenteVoz(String assistenteVoz) { this.assistenteVoz = assistenteVoz; }
}
