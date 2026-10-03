package com.gestao.api.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.context.UserContext;
import com.gestao.api.controllers.DTOs.DispositivoPushRequestDTO;
import com.gestao.api.entities.DispositivoPush;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.PlataformaDispositivo;
import com.gestao.api.repositories.DispositivoPushRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class DispositivoPushService {

    private final DispositivoPushRepository repository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public DispositivoPushService(DispositivoPushRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Registra ou reativa o token. Se ele estava com outro usuário (troca de conta no aparelho), passa para o atual. */
    @Transactional
    public void registrar(DispositivoPushRequestDTO dto) {
        Long usuarioId = UserContext.getIdUsuario();
        Instant agora = clock.instant();
        String token = dto.token().trim();

        DispositivoPush dispositivo = repository.findByExpoPushToken(token).orElseGet(() -> {
            DispositivoPush novo = new DispositivoPush();
            novo.setExpoPushToken(token);
            novo.setCriadoEm(agora);
            return novo;
        });

        dispositivo.setUsuario(entityManager.getReference(Usuario.class, usuarioId));
        dispositivo.setPlataforma(PlataformaDispositivo.valueOf(dto.plataforma().trim().toUpperCase(Locale.ROOT)));
        dispositivo.setAtivo(true);
        dispositivo.setUltimoUsoEm(agora);
        repository.save(dispositivo);
    }

    /** Logout: só o dono desativa o próprio token. */
    @Transactional
    public void remover(String token) {
        Long usuarioId = UserContext.getIdUsuario();
        repository.findByExpoPushToken(token.trim())
                .filter(d -> usuarioId.equals(d.getUsuario().getId()))
                .ifPresent(d -> {
                    d.setAtivo(false);
                    repository.save(d);
                });
    }
}
