package com.gestao.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.gestao.api.controllers.DTOs.DispositivoPushRequestDTO;
import com.gestao.api.entities.DispositivoPush;
import com.gestao.api.entities.Usuario;
import com.gestao.api.enuns.PlataformaDispositivo;
import com.gestao.api.repositories.DispositivoPushRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class DispositivoPushServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-04T12:00:30Z");
    private static final String TOKEN = "ExponentPushToken[abc]";
    private static final DispositivoPushRequestDTO DTO = new DispositivoPushRequestDTO(TOKEN, "android");

    private DispositivoPushRepository repository;
    private EntityManager entityManager;
    private Query query;
    private DispositivoPushService service;

    @BeforeEach
    void setUp() {
        repository = mock(DispositivoPushRepository.class);
        entityManager = mock(EntityManager.class);
        query = mock(Query.class, RETURNS_SELF);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        service = new DispositivoPushService(repository, Clock.fixed(AGORA, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        autenticar(7L);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokenAtivoDoMesmoUsuarioNaoEscreve() {
        when(repository.findByExpoPushToken(TOKEN)).thenReturn(Optional.of(dispositivo(7L, true, AGORA.minusSeconds(60))));

        assertThat(service.registrar(DTO)).isFalse();
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void tokenNovoFazUpsert() {
        when(repository.findByExpoPushToken(TOKEN)).thenReturn(Optional.empty());

        assertThat(service.registrar(DTO)).isTrue();
        verify(query).setParameter(1, 7L);
        verify(query).setParameter(2, TOKEN);
        verify(query).executeUpdate();
    }

    @Test
    void tokenDeOutroUsuarioEReatribuido() {
        when(repository.findByExpoPushToken(TOKEN)).thenReturn(Optional.of(dispositivo(99L, true, AGORA)));

        assertThat(service.registrar(DTO)).isTrue();
        verify(query).setParameter(1, 7L);
        verify(query).executeUpdate();
    }

    @Test
    void tokenInativoEReativado() {
        when(repository.findByExpoPushToken(TOKEN)).thenReturn(Optional.of(dispositivo(7L, false, AGORA)));

        assertThat(service.registrar(DTO)).isTrue();
        verify(query).executeUpdate();
    }

    @Test
    void ultimoUsoRenovadoNoMaximoUmaVezPorHora() {
        when(repository.findByExpoPushToken(TOKEN))
                .thenReturn(Optional.of(dispositivo(7L, true, AGORA.minus(DispositivoPushService.RENOVAR_ULTIMO_USO).minusSeconds(1))));

        assertThat(service.registrar(DTO)).isTrue();
        verify(query).executeUpdate();
    }

    @Test
    void corridaSemLinhaAfetadaViraNoContent() {
        when(repository.findByExpoPushToken(TOKEN)).thenReturn(Optional.empty());
        when(query.executeUpdate()).thenReturn(0);

        assertThat(service.registrar(DTO)).isFalse();
    }

    @Test
    void contaChamadasPorUsuarioSemBloquear() {
        when(repository.findByExpoPushToken(TOKEN)).thenReturn(Optional.of(dispositivo(7L, true, AGORA)));

        for (int i = 0; i < DispositivoPushService.LIMITE_POR_MINUTO * 3; i++) {
            assertThat(service.registrar(DTO)).isFalse();
        }
        assertThat(service.chamadasNoMinuto(7L)).isEqualTo(DispositivoPushService.LIMITE_POR_MINUTO * 3);
    }

    private static DispositivoPush dispositivo(Long usuarioId, boolean ativo, Instant ultimoUso) {
        Usuario dono = new Usuario();
        dono.setId(usuarioId);
        DispositivoPush d = new DispositivoPush();
        d.setUsuario(dono);
        d.setExpoPushToken(TOKEN);
        d.setPlataforma(PlataformaDispositivo.ANDROID);
        d.setAtivo(ativo);
        d.setUltimoUsoEm(ultimoUso);
        return d;
    }

    private static void autenticar(Long usuarioId) {
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, java.util.List.of()));
    }
}
