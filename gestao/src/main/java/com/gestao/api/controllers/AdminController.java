package com.gestao.api.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;

import com.gen.core.api.AbstractController;
import com.gen.core.api.EndpointMapping;
import com.gen.core.api.MethodMapping;
import com.gestao.api.admin.bo.AdminBO;

/**
 * Painel super-admin (backoffice do dono). Controller-as-router: zero lógica,
 * apenas delega ao {@link AdminBO}. Toda a classe é protegida por SUPER_ADMIN.
 */
@EndpointMapping("/api/v1/z_admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController extends AbstractController {

    private final AdminBO adminBO;

    public AdminController(AdminBO adminBO) {
        this.adminBO = adminBO;
    }

    @MethodMapping(path = "/metricas", type = RequestMethod.GET)
    public ResponseEntity<?> metricas() {
        return ResponseEntity.ok(adminBO.metricas());
    }

    @MethodMapping(path = "/tenants", type = RequestMethod.GET)
    public ResponseEntity<?> listarTenants() {
        return ResponseEntity.ok(adminBO.listarTenants());
    }

    @MethodMapping(path = "/tenants/{id}", type = RequestMethod.GET)
    public ResponseEntity<?> detalheTenant(@PathVariable Long id) {
        return ResponseEntity.ok(adminBO.detalheTenant(id));
    }

    @MethodMapping(path = "/tenants/{id}/inativar", type = RequestMethod.PATCH)
    public ResponseEntity<?> inativar(@PathVariable Long id) {
        adminBO.inativar(id);
        return ResponseEntity.ok(adminBO.detalheTenant(id));
    }

    @MethodMapping(path = "/tenants/{id}/ativar", type = RequestMethod.PATCH)
    public ResponseEntity<?> ativar(@PathVariable Long id) {
        adminBO.ativar(id);
        return ResponseEntity.ok(adminBO.detalheTenant(id));
    }
}
