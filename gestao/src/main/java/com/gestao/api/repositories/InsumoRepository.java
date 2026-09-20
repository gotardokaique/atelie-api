package com.gestao.api.repositories;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gestao.api.entities.Insumo;

@Repository
public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    @Modifying
    @Query("UPDATE Insumo i SET i.saldo = i.saldo - :qtd " +
            "WHERE i.id = :id AND i.saldo >= :qtd")
    int baixarSaldo(@Param("id") Long id, @Param("qtd") BigDecimal qtd);
}
