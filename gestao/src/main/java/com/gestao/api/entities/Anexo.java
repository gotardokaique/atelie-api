package com.gestao.api.entities;

import java.util.UUID;

import com.gen.core.constants.SitId;
import com.gen.core.domain.AbstractEntity;
import com.gen.core.domain.AbstractEntity.Tabela;
import com.gestao.api.enuns.TipoAnexo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "anexo", indexes = {
        @Index(name = "idx_anexo_tenant_user", columnList = "ane_tenant_id, ane_user_id"),
        @Index(name = "idx_anexo_type_status", columnList = "ane_type, ane_status_id")
})
@Tabela(prefixo = "ane")
public class Anexo extends AbstractEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "ane_uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid = UUID.randomUUID();

    @Column(name = "ane_tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "ane_user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ane_type", nullable = false, length = 30)
    private TipoAnexo type;

    @Column(name = "ane_name", nullable = false, length = 255)
    private String name;

    @Column(name = "ane_content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "ane_size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "ane_status_id", nullable = false)
    private Integer statusId = SitId.PENDENTE;

    public Anexo() {
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public TipoAnexo getType() {
        return type;
    }

    public void setType(TipoAnexo type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Integer getStatusId() {
        return statusId;
    }

    public void setStatusId(Integer statusId) {
        this.statusId = statusId;
    }
}