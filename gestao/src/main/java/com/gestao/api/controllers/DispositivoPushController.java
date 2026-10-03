package com.gestao.api.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gen.core.api.ApiResponse;
import com.gestao.api.controllers.DTOs.DispositivoPushRequestDTO;
import com.gestao.api.services.DispositivoPushService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/dispositivos")
public class DispositivoPushController extends AbstractController {

    private final DispositivoPushService dispositivoService;

    public DispositivoPushController(DispositivoPushService dispositivoService) {
        this.dispositivoService = dispositivoService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> registrar(@RequestBody @Valid DispositivoPushRequestDTO dto) {
        dispositivoService.registrar(dto);
        return ResponseEntity.ok(ApiResponse.okMessage("Aparelho registrado para notificações."));
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable String token) {
        dispositivoService.remover(token);
        return ResponseEntity.ok(ApiResponse.okMessage("Aparelho removido das notificações."));
    }
}
