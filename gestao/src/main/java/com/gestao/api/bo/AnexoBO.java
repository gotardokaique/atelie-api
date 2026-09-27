//package com.gestao.api.bo;
//
//import java.util.UUID;
//
//import com.gen.core.bo.AbstractBO;
//import com.gen.core.constants.SitId;
//import com.gen.core.context.UserContext;
//import com.gen.core.db.TransactionDB;
//import com.gen.core.db.exception.NotFoundException;
//import com.gestao.api.controllers.DTOs.AnexoRequestDTO;
//import com.gestao.api.entities.Anexo;
//
///**
// * Business Object do Anexo.
// *
// * Concentra as regras: montagem da chave de storage, transições de situação
// * e validação de acesso por tenant.
// *
// * Instancie com new a cada operação.
// */
//public class AnexoBO extends AbstractBO<AnexoRequestDTO> {
//
//    private Anexo anexo;
//
//    public AnexoBO(TransactionDB transactionDB, UserContext userContext) {
//        super(transactionDB, userContext);
//    }
//
//    // =====================================================================
//    // CICLO
//    // =====================================================================
//
//    @Override
//    public void init() {
//        this.anexo = new Anexo();
//        this.anexo.setUuid(UUID.randomUUID());
//        this.anexo.setStatusId(SitId.PENDENTE);
//        this.anexo.setTenantId(userContext.getIdUnidade());
//        this.anexo.setUserId(userContext.getIdUsuario());
//        super.markStarted();
//    }
//
//    @Override
//    public void load(Long id) {
//        if (id == null)
//            throw new IllegalArgumentException("id is required");
//
//        try {
//            this.anexo = transactionDB.select().from(Anexo.class).id(id);
//        } catch (Exception e) {
//            throw new NotFoundException("Anexo não encontrado. ID=" + id);
//        }
//
//        if (this.anexo == null)
//            throw new NotFoundException("Anexo não encontrado. ID=" + id);
//
//        validateAccess();
//        super.markStarted();
//    }
//
//    @Override
//    public void setData(AnexoRequestDTO dto) {
//        super.requireStarted();
//
//        if (dto == null)
//            throw new IllegalArgumentException("dto is required");
//
//        anexo.setType(dto.type());
//        anexo.setName(dto.name());
//        anexo.setContentType(dto.contentType());
//        anexo.setSizeBytes(dto.sizeBytes());
//    }
//
//    @Override
//    public void save() {
//        super.requireStarted();
//        validate();
//
//        try {
//            if (anexo.getId() == null) {
//                transactionDB.insert(anexo);
//            } else {
//                transactionDB.update(anexo);
//            }
//        } catch (Exception e) {
//            throw new IllegalStateException("Falha ao gravar anexo: " + e.getMessage(), e);
//        }
//    }
//
//    // =====================================================================
//    // REGRAS
//    // =====================================================================
//
//    /** Chave do objeto no storage. Derivada da identidade do anexo. */
//    public String chave() {
//        super.requireStarted();
//        return "%d/%d/%s".formatted(anexo.getTenantId(), anexo.getUserId(), anexo.getUuid());
//    }
//
//    /**
//     * Marca como concluído com os dados REAIS do arquivo — os detectados por
//     * magic byte após a gravação no storage, não os declarados pelo cliente.
//     */
//    public void confirmar(String contentTypeReal, Long sizeReal) {
//        super.requireStarted();
//        anexo.setContentType(contentTypeReal);
//        anexo.setSizeBytes(sizeReal);
//        anexo.setStatusId(SitId.CONCLUIDO);
//    }
//
//    /** Marca como cancelado e inativa. Use quando a validação do arquivo falhar. */
//    public void rejeitar() {
//        super.requireStarted();
//        anexo.setStatusId(SitId.CANCELADO);
//        anexo.desativar();
//    }
//
//    public boolean isConfirmado() {
//        super.requireStarted();
//        return SitId.CONCLUIDO.equals(anexo.getStatusId());
//    }
//
//    // =====================================================================
//    // VALIDAÇÕES
//    // =====================================================================
//
//    /** Consistência mínima antes de persistir. */
//    private void validate() {
//        if (anexo.getTenantId() == null)
//            throw new IllegalStateException("tenantId não definido");
//        if (anexo.getUserId() == null)
//            throw new IllegalStateException("userId não definido");
//        if (anexo.getType() == null)
//            throw new IllegalStateException("tipo do anexo não definido");
//        if (anexo.getName() == null || anexo.getName().isBlank())
//            throw new IllegalStateException("nome do anexo não definido");
//        if (anexo.getSizeBytes() == null || anexo.getSizeBytes() <= 0)
//            throw new IllegalStateException("tamanho do anexo inválido");
//    }
//
//    /** Impede acesso a anexo de outro tenant. */
//    private void validateAccess() {
//        if (!anexo.getTenantId().equals(userContext.getTenantId()))
//            throw new NotFoundException("Anexo não encontrado. ID=" + anexo.getId());
//    }
//
//    // =====================================================================
//
//    public Anexo getAnexo() {
//        super.requireStarted();
//        return anexo;
//    }
//}