package com.gestao.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gen.core.db.QueryBuilder;
import com.gen.core.db.TransactionDB;

/**
 * Registra os beans do gen-core que NÃO possuem @Component e portanto
 * não são detectados automaticamente pelo Spring component scan.
 *
 * Beans que JÁ são @Component no gen-core (não registrar aqui para evitar duplicatas):
 * - TransactionDB (@Component + @PersistenceContext)
 * - DAOController (@Component)
 * - TokenService (@Service)
 * - SessionService (@Service)
 * - JwtTokenProvider (@Component)
 * - StringEncryptUtils (@Component + @Value)
 */
@Configuration
public class GenCoreBeansConfig {

    /**
     * QueryBuilder não possui @Component no gen-core — precisa ser declarado
     * explicitamente. Usa o TransactionDB já registrado como @Component.
     */
    @Bean
    public QueryBuilder queryBuilder(TransactionDB transactionDB) {
        return new QueryBuilder(transactionDB);
    }
}
