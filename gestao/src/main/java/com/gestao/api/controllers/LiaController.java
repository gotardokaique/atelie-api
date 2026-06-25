package com.gestao.api.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gestao.api.config.lia.service.LiaOrchestrator;
import com.gestao.api.context.UserContext;

@RestController
@RequestMapping("/api/ai/")
public class LiaController extends AbstractController {

    private final LiaOrchestrator orchestrator;

    public LiaController(LiaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody LiaChatRequest request) {
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

        public boolean confirmadoFlag() {
            return Boolean.TRUE.equals(confirmado);
        }
    }
}
