package com.gestao.api.db;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DAOController {

    private final TransactionDB transactionDB;

    public DAOController(TransactionDB transactionDB) {
        this.transactionDB = transactionDB;
    }

    @Transactional
    public <T> T insert(T entity) {
        return transactionDB.insert(entity);
    }

    @Transactional
    public <T> T update(T entity) {
        return transactionDB.update(entity);
    }

    @Transactional
    public <T> T delete(T entity) {
        return transactionDB.deleteEntity(entity);
    }

    public QueryBuilder select() {
        return new QueryBuilder(transactionDB).select();
    }

    public QueryBuilder select(String... campos) {
        return new QueryBuilder(transactionDB).select(campos);
    }
}