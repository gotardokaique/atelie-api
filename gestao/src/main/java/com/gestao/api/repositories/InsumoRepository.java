package com.gestao.api.repositories;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gestao.api.entities.Insumo;

/**
 * Repositório fino — existe só para a baixa atômica de saldo, que o padrão
 * {@code DAOController}/{@code QueryBuilder} não expressa (não há UPDATE-by-query).
 * Todo o resto do CRUD/consulta de insumo passa pelo {@code DAOController}.
 */
@Repository
public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    /**
     * Baixa atômica com guarda. Resolve concorrência sem lock pessimista:
     * o {@code WHERE saldo >= :qtd} garante que duas saídas simultâneas não
     * derrubem o saldo abaixo de zero.
     *
     * @return número de linhas afetadas — 0 significa saldo insuficiente.
     */
    @Modifying
    @Query("UPDATE Insumo i SET i.saldo = i.saldo - :qtd " +
            "WHERE i.id = :id AND i.saldo >= :qtd")
    int baixarSaldo(@Param("id") Long id, @Param("qtd") BigDecimal qtd);
}
