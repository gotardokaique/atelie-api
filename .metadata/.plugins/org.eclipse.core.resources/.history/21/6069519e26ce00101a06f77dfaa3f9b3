package com.gen.core.db;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

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

    private EntityManager em() {
        return transactionDB.getEntityManager();
    }

    public QueryBuilder select() {
        return new QueryBuilder(em()).select();
    }

    public QueryBuilder select(String... campos) {
        return new QueryBuilder(em()).select(campos);
    }
}
