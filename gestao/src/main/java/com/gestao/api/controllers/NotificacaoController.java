package com.gestao.api.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gen.core.api.ApiResponse;
import com.gen.core.db.PageResult;
import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.EnvioPushDTO;
import com.gestao.api.controllers.DTOs.NotificacaoContagemDTO;
import com.gestao.api.controllers.DTOs.NotificacaoDTO;
import com.gestao.api.entities.Notificacao;
import com.gestao.api.services.NotificacaoPushService;
import com.gestao.api.services.NotificacaoService;

@RestController
@RequestMapping("/api/v1/notificacoes")
public class NotificacaoController extends AbstractController {

    private final NotificacaoService notificacaoService;
    private final NotificacaoPushService pushService;

    public NotificacaoController(NotificacaoService notificacaoService, NotificacaoPushService pushService) {
        this.notificacaoService = notificacaoService;
        this.pushService = pushService;
    }

    @GetMapping
    public ResponseEntity<PageResult<NotificacaoDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificacaoService.listar(page, size));
    }

    @GetMapping("/contagem")
    public ResponseEntity<NotificacaoContagemDTO> contagem() {
        return ResponseEntity.ok(notificacaoService.contagem());
    }

    @PatchMapping("/visualizar-todas")
    public ResponseEntity<NotificacaoContagemDTO> visualizarTodas() {
        return ResponseEntity.ok(notificacaoService.visualizarTodas());
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<NotificacaoDTO> marcarLida(@PathVariable Long id) {
        return ResponseEntity.ok(notificacaoService.marcarLida(id));
    }

    @PatchMapping("/ler-todas")
    public ResponseEntity<NotificacaoContagemDTO> lerTodas() {
        return ResponseEntity.ok(notificacaoService.lerTodas());
    }

    @PostMapping("/teste")
    public ResponseEntity<ApiResponse<EnvioPushDTO>> teste() {
        Long usuarioId = UserContext.getIdUsuario();
        Notificacao teste = notificacaoService.criarTeste(usuarioId);
        EnvioPushDTO envio = pushService.enviar(usuarioId, List.of(teste));
        return ResponseEntity.ok(ApiResponse.ok(envio, mensagemTeste(envio)));
    }

    private static String mensagemTeste(EnvioPushDTO envio) {
        if (envio.dispositivos() == 0) {
            return "Notificação criada, mas nenhum aparelho está registrado para push.";
        }
        if (envio.enviados() == 0) {
            return "Notificação criada, mas o push falhou: " + envio.erro();
        }
        return "Push de teste enviado para " + envio.enviados() + " aparelho(s).";
    }
}
