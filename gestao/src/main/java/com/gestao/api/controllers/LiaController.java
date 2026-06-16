package com.gestao.api.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestao.api.config.lia.service.LiaOrchestrator;
import com.gestao.api.context.UserContext;

/**
 * Endpoint da Lia. Mantém o contrato JSON anterior (toMap()) para não quebrar o
 * frontend existente, mas internamente já trabalha com tipos.
 */
@RestController
@RequestMapping("/api/ai/")
public class LiaController {

    private final LiaOrchestrator orchestrator;

    public LiaController(LiaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody LiaChatRequest request) {
        // sitId e usuId vêm sempre do usuário autenticado — nunca do corpo da request,
        // para não expor identificadores numéricos ao frontend.
        Long usuId = UserContext.getIdUsuario();
        Long sitId = usuId;

        var response = orchestrator.process(
                sitId,
                usuId,
                request.mensagemOuVazia(),
                request.confirmadoFlag(),
                request.toolName(),
                request.toolInput());
        return ResponseEntity.ok(response.toMap());
    }

    public record LiaChatRequest(
            String mensagem,
            Boolean confirmado,
            String toolName,
            Map<String, Object> toolInput) {

        public String mensagemOuVazia() {
            return mensagem == null ? "" : mensagem;
        }

        // Wrapper Boolean: o front pode mandar a flag ausente OU null. Normalizamos
        // para false em vez de quebrar a desserialização (primitivo não aceita null).
        // Nome distinto do componente do record (accessor canônico não pode mudar o tipo).
        public boolean confirmadoFlag() {
            return Boolean.TRUE.equals(confirmado);
        }
    }
}
