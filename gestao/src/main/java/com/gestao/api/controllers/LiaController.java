package com.gestao.api.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestao.api.config.lia.service.LiaOrchestrator;

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
        var response = orchestrator.process(
                request.messages(),
                request.confirmado(),
                request.toolName(),
                request.toolInput());
        return ResponseEntity.ok(response.toMap());
    }

    public record LiaChatRequest(
            List<Map<String, Object>> messages,
            boolean confirmado,
            String toolName,
            Map<String, Object> toolInput) {

        public List<Map<String, Object>> messages() {
            return messages == null ? List.of() : messages;
        }
    }
}
