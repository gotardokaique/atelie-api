package com.gestao.api.admin.bo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gen.core.db.TransactionDB;
import com.gestao.api.admin.dto.MetricasDTO;
import com.gestao.api.admin.dto.TenantDetalheDTO;
import com.gestao.api.admin.dto.TenantListItemDTO;
import com.gestao.api.admin.service.AdminConsultaService;
import com.gestao.api.context.UserContext;
import com.gestao.api.entities.Usuario;
import com.gestao.api.services.exceptions.NotFoundException;

import java.util.List;

@Component
public class AdminBO {

    private static final Logger log = LoggerFactory.getLogger(AdminBO.class);

    private final AdminConsultaService consultaService;
    private final TransactionDB trans;

    public AdminBO(AdminConsultaService consultaService, TransactionDB trans) {
        this.consultaService = consultaService;
        this.trans = trans;
    }

    // ===================== LEITURA (delega) =====================

    public MetricasDTO metricas() {
        return consultaService.metricasGlobais();
    }

    public List<TenantListItemDTO> listarTenants() {
        return consultaService.listarTenants();
    }

    public TenantDetalheDTO detalheTenant(Long id) {
        return consultaService.detalheTenant(id);
    }

    // ===================== MUTAÇÃO =====================

    @Transactional
    public void inativar(Long id) {
        Usuario tenant = buscarTenant(id);
        tenant.setAtivo(false);
        trans.update(tenant);
        log.info("[Z_ADMIN] Tenant inativado: id={} por admin={} ", id, UserContext.getEmailUsuario());
    }

    @Transactional
    public void ativar(Long id) {
        Usuario tenant = buscarTenant(id);
        tenant.setAtivo(true);
        trans.update(tenant);
        log.info("[Z_ADMIN] Tenant ativado: id={} por admin={} ", id, UserContext.getEmailUsuario());
    }

    private Usuario buscarTenant(Long id) {
        Usuario tenant = trans.selectById(Usuario.class, id);
        if (tenant == null) {
            throw new NotFoundException("Tenant não encontrado com id: " + id);
        }
        return tenant;
    }
}
