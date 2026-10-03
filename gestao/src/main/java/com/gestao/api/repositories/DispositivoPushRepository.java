package com.gestao.api.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.entities.DispositivoPush;

@Repository
public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {

    Optional<DispositivoPush> findByExpoPushToken(String expoPushToken);

    List<DispositivoPush> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    @Query("SELECT d.usuario.id, COUNT(d) FROM DispositivoPush d "
            + "WHERE d.ativo = true AND d.usuario.id IN :usuarioIds GROUP BY d.usuario.id")
    List<Object[]> contarAtivosPorUsuario(@Param("usuarioIds") Collection<Long> usuarioIds);

    @Transactional
    @Modifying
    @Query("UPDATE DispositivoPush d SET d.ativo = false WHERE d.expoPushToken IN :tokens AND d.ativo = true")
    int desativar(@Param("tokens") Collection<String> tokens);
}
