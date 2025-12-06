package com.gen.core.db;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

@Component
public class TransactionDB {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public <T> T insert(T entity) {
        Objects.requireNonNull(entity, "entidade não pode ser nula");
        entityManager.persist(entity);
        return entity;
    }

    @Transactional
    public <T> T update(T entity) {
        Objects.requireNonNull(entity, "entidade não pode ser nula");
        return entityManager.merge(entity);
    }

    @Transactional
    public <T> void delete(Class<T> type, Integer id) {
        Objects.requireNonNull(type, "tipo não pode ser nulo");
        Objects.requireNonNull(id, "id não pode ser nulo");
        T ref = entityManager.getReference(type, id);
        entityManager.remove(ref);
    }

    @Transactional(readOnly = true)
    public <T> T selectById(Class<T> type, Integer id) {
        Objects.requireNonNull(type, "tipo não pode ser nulo");
        Objects.requireNonNull(id, "id não pode ser nulo");
        return entityManager.find(type, id);
    }

    @Transactional(readOnly = true)
    public <T> List<T> selectAll(Class<T> type) {
        Objects.requireNonNull(type, "tipo não pode ser nulo");
        String jpql = "SELECT e FROM " + type.getSimpleName() + " e";
        return entityManager.createQuery(jpql, type).getResultList();
    }

    @Transactional(readOnly = true)
    public <T> List<T> selectByUser(String tabela, Integer userId, Class<T> tipo) {
        Objects.requireNonNull(tabela, "tabela não pode ser nula");
        Objects.requireNonNull(userId, "userId não pode ser nulo");
        String sql = "SELECT * FROM " + tabela + " WHERE user_id = :userId";
        Query query = entityManager.createNativeQuery(sql, tipo);
        query.setParameter("userId", userId);
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public <T> List<T> selectFilter(String tabela, String coluna, String filtro, Integer userId, Class<T> tipo) {
        Objects.requireNonNull(tabela, "tabela não pode ser nula");
        Objects.requireNonNull(coluna, "coluna não pode ser nula");
        Objects.requireNonNull(userId, "userId não pode ser nulo");
        String sql = "SELECT * FROM " + tabela + " WHERE user_id = :userId AND " + coluna + " ILIKE :filtro";
        Query query = entityManager.createNativeQuery(sql, tipo);
        query.setParameter("userId", userId);
        query.setParameter("filtro", "%" + filtro + "%");
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public <T> List<T> selectQuery(String jpql, Class<T> type) {
        Objects.requireNonNull(jpql, "jpql não pode ser nulo");
        TypedQuery<T> query = entityManager.createQuery(jpql, type);
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public List<?> select(String sql) {
        Objects.requireNonNull(sql, "sql não pode ser nulo");
        Query query = entityManager.createNativeQuery(sql);
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public <T> List<T> selectPaged(String jpql, Class<T> type, Integer page, Integer size) {
        if (page == null || size == null || page < 0 || size <= 0) {
            return Collections.emptyList();
        }
        TypedQuery<T> query = entityManager.createQuery(jpql, type);
        query.setFirstResult(page * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public Integer count(String jpql) {
        Objects.requireNonNull(jpql, "jpql não pode ser nulo");
        Query query = entityManager.createQuery(jpql);
        Object result = query.getSingleResult();
        return result instanceof Number ? ((Number) result).intValue() : 0;
    }

    @Transactional(readOnly = true)
    public boolean exists(String jpql) {
        return count(jpql) > 0;
    }

    @Transactional
    public void deleteAll(Class<?> type) {
        Objects.requireNonNull(type, "tipo não pode ser nulo");
        String jpql = "DELETE FROM " + type.getSimpleName();
        entityManager.createQuery(jpql).executeUpdate();
    }

    @Transactional(readOnly = true)
    public <T> T firstResult(String jpql, Class<T> type) {
        Objects.requireNonNull(jpql, "jpql não pode ser nulo");
        List<T> list = entityManager.createQuery(jpql, type).setMaxResults(1).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    @Transactional
    public void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
    
    public LocalDate getDataAtual() {
        return LocalDate.now();
    }


    public LocalDateTime getDataHoraAtual() {
        return LocalDateTime.now();
    }


    public LocalTime getHoraAtual() {
        return LocalTime.now();
    }


    public Date getDataAtualDate() {
        return Date.from(LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant());
    }

    public Date getDataHoraAtualDate() {
        return Date.from(LocalDateTime.now()
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }
    
    EntityManager getEntityManager() {
        return entityManager;
    }

}
