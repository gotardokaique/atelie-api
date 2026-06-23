package com.gestao.api.admin.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.Condicao;
import com.gen.core.db.DAOController;
import com.gestao.api.admin.dto.MetricasDTO;
import com.gestao.api.admin.dto.TenantDetalheDTO;
import com.gestao.api.admin.dto.TenantListItemDTO;
import com.gestao.api.entities.Pessoa;
import com.gestao.api.entities.Servico;
import com.gestao.api.entities.Usuario;
import com.gestao.api.services.exceptions.NotFoundException;

/**
 * Leitura agregada do painel super-admin.
 *
 * <p>Por regra de privacidade, este service NUNCA expõe conteúdo pessoal do tenant
 * (nomes de clientes, descrições de serviço, valores). Apenas contadores agregados.
 */
@Component
public class AdminConsultaService {

    private final DAOController daoController;

    public AdminConsultaService(DAOController daoController) {
        this.daoController = daoController;
    }

    // ===================== CONTADORES POR TENANT =====================

    @Transactional(readOnly = true)
    public long contarServicosPorUsuario(Long usuarioId) {
        List<Servico> servicos = daoController
                .select()
                .from(Servico.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, usuarioId)
                .list();

        return servicos.size();
    }

    @Transactional(readOnly = true)
    public long contarClientesPorUsuario(Long usuarioId) {
        List<Pessoa> clientes = daoController
                .select()
                .from(Pessoa.class)
                .join("usuario")
                .where("usuario.id", Condicao.EQUAL, usuarioId)
                .list();

        return clientes.size();
    }

    // ===================== MÉTRICAS GLOBAIS =====================

    @Transactional(readOnly = true)
    public MetricasDTO metricasGlobais() {
        List<Usuario> usuarios = daoController
                .select()
                .from(Usuario.class)
                .list();

        long total = usuarios.size();
        long ativos = usuarios.stream()
                .filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                .count();
        long inativos = total - ativos;

        YearMonth mesAtual = YearMonth.now();
        LocalDateTime inicioMes = mesAtual.atDay(1).atStartOfDay();
        LocalDateTime fimMes = mesAtual.atEndOfMonth().atTime(23, 59, 59);

        long novosNoMes = usuarios.stream()
                .map(Usuario::getDataCadastro)
                .filter(d -> d != null && !d.isBefore(inicioMes) && !d.isAfter(fimMes))
                .count();

        return new MetricasDTO(total, ativos, inativos, novosNoMes);
    }

    // ===================== LISTAGEM DE TENANTS =====================

    @Transactional(readOnly = true)
    public List<TenantListItemDTO> listarTenants() {
        List<Usuario> usuarios = daoController
                .select()
                .from(Usuario.class)
                .orderBy("dataCadastro", false)
                .list();

        return usuarios.stream()
                .map(u -> new TenantListItemDTO(
                        u.getId(),
                        u.getNome(),
                        u.getEmail(),
                        u.getProvider() != null ? u.getProvider().name() : null,
                        u.getAtivo(),
                        u.getDataCadastro(),
                        contarServicosPorUsuario(u.getId()),
                        contarClientesPorUsuario(u.getId())))
                .toList();
    }

    // ===================== DETALHE DE UM TENANT =====================

    @Transactional(readOnly = true)
    public TenantDetalheDTO detalheTenant(Long id) {
        Usuario usuario = buscarUsuario(id);

        return new TenantDetalheDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getProvider() != null ? usuario.getProvider().name() : null,
                usuario.getAtivo(),
                usuario.getDataCadastro(),
                usuario.getDataAtualizacao(),
                contarServicosPorUsuario(usuario.getId()),
                contarClientesPorUsuario(usuario.getId()));
    }

    private Usuario buscarUsuario(Long id) {
        try {
            return daoController
                    .select()
                    .from(Usuario.class)
                    .id(id);
        } catch (NotFoundException e) {
            throw new NotFoundException("Tenant não encontrado com id: " + id);
        }
    }
}
