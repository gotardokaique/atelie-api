package com.gestao.api.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.entities.Notificacao;

/** Atualizações em lote e contagens que o DAOController não expressa. */
@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    Page<Notificacao> findByUsuarioIdOrderByCriadaEmDescIdDesc(Long usuarioId, Pageable pageable);

    Optional<Notificacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByChaveDedupe(String chaveDedupe);

    @Query("SELECT COUNT(n) FROM Notificacao n WHERE n.usuario.id = :usuarioId AND n.visualizadaEm IS NULL")
    long contarNaoVisualizadas(@Param("usuarioId") Long usuarioId);

    @Query("SELECT COUNT(n) FROM Notificacao n WHERE n.usuario.id = :usuarioId AND n.lidaEm IS NULL")
    long contarNaoLidas(@Param("usuarioId") Long usuarioId);

    @Transactional
    @Modifying
    @Query("UPDATE Notificacao n SET n.visualizadaEm = :agora "
            + "WHERE n.usuario.id = :usuarioId AND n.visualizadaEm IS NULL")
    int visualizarTodas(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    @Transactional
    @Modifying
    @Query("UPDATE Notificacao n SET n.lidaEm = :agora, "
            + "n.visualizadaEm = COALESCE(n.visualizadaEm, :agora) "
            + "WHERE n.usuario.id = :usuarioId AND n.lidaEm IS NULL")
    int lerTodas(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    @Transactional
    @Modifying
    @Query("UPDATE Notificacao n SET n.pushEnviadoEm = :agora WHERE n.id = :id AND n.pushEnviadoEm IS NULL")
    int marcarPushEnviado(@Param("id") Long id, @Param("agora") Instant agora);
}
