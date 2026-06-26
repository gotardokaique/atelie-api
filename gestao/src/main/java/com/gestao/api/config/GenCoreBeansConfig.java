package com.gestao.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gen.core.db.QueryBuilder;
import com.gen.core.db.TransactionDB;

@Configuration
public class GenCoreBeansConfig {

    @Bean
    public QueryBuilder queryBuilder(TransactionDB transactionDB) {
        return new QueryBuilder(transactionDB);
    }
}
