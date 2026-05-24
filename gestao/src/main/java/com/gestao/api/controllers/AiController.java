package com.gestao.api.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestao.api.services.AiService;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        boolean confirmado = (boolean) body.getOrDefault("confirmado", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> toolInput = (Map<String, Object>) body.get("toolInput");
        String toolName = (String) body.get("toolName");

        return aiService.processChat(messages, confirmado, toolName, toolInput);
    }
}