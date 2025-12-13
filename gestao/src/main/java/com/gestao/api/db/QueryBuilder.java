package com.gestao.api.db;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.gestao.api.dev.DevSqlLogger;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

public class QueryBuilder {


    private static final Logger log = LoggerFactory.getLogger(QueryBuilder.class);

    private final EntityManager entityManager;

    private final StringBuilder jpql = new StringBuilder();
    private final List<Object> params = new ArrayList<>();

    private boolean hasWhere = false;
    private String rootAlias = "c";
    private Class<?> entityClass;
    private String entityName;

    private boolean projection = false;
    private final List<String> selectedRawFields = new ArrayList<>();
    private Integer maxResults;

    public QueryBuilder(TransactionDB transactionDB) {
        this.entityManager = transactionDB.getEntityManager();
    }

    private void reset() {
        jpql.setLength(0);
        params.clear();
        hasWhere = false;
        rootAlias = "c";
        entityClass = null;
        entityName = null;
        projection = false;
        selectedRawFields.clear();
        maxResults = null;
    }

    public QueryBuilder select() {
        reset();
        jpql.append("SELECT ").append(rootAlias).append(" ");
        return this;
    }

    public QueryBuilder select(String... campos) {
        reset();

        if (campos == null || campos.length == 0) {
            jpql.append("SELECT ").append(rootAlias).append(" ");
            return this;
        }

        projection = true;
        jpql.append("SELECT ");

        for (int i = 0; i < campos.length; i++) {
            String campo = campos[i];
            if (campo == null || campo.isBlank()) {
                continue;
            }

            String qualified = qualifyField(campo);

            if (i > 0) {
                jpql.append(", ");
            }
            jpql.append(qualified);

            selectedRawFields.add(campo.trim());
        }

        jpql.append(" ");
        return this;
    }

    public QueryBuilder from(Class<?> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass não pode ser nulo");
        }

        this.entityClass = entityClass;
        this.entityName = entityClass.getSimpleName();

        jpql.append("FROM ")
            .append(entityName)
            .append(" ")
            .append(rootAlias)
            .append(" ");

        if (projection && !selectedRawFields.isEmpty()) {
            validateSelectedFieldsAgainstEntity();
        }

        return this;
    }

    public QueryBuilder from(String entityOrExpression) {
        if (entityOrExpression == null || entityOrExpression.isBlank()) {
            throw new IllegalArgumentException("FROM não pode ser vazio");
        }

        String trimmed = entityOrExpression.trim();
        String[] parts = trimmed.split("\\s+");

        if (parts.length == 1) {
            entityName = parts[0];
            rootAlias = "c";
        } else {
            entityName = parts[0];
            rootAlias = parts[1];
        }

        entityClass = null;

        jpql.append("FROM ")
            .append(entityName)
            .append(" ")
            .append(rootAlias)
            .append(" ");

        if (projection && !selectedRawFields.isEmpty()) {
            log.debug("Não é possível validar os campos de select() sem entityClass definida (from(String)).");
        }

        return this;
    }

    public QueryBuilder join(String associationPath) {
        return joinInternal("JOIN", associationPath, null);
    }

    public QueryBuilder join(String associationPath, String alias) {
        return joinInternal("JOIN", associationPath, alias);
    }

    public QueryBuilder leftJoin(String associationPath) {
        return joinInternal("LEFT JOIN", associationPath, null);
    }

    public QueryBuilder leftJoin(String associationPath, String alias) {
        return joinInternal("LEFT JOIN", associationPath, alias);
    }

    private QueryBuilder joinInternal(String joinType, String associationPath, String aliasOverride) {
        if (associationPath == null || associationPath.isBlank()) {
            throw new IllegalArgumentException("associationPath do JOIN não pode ser nulo/vazio");
        }

        String trimmed = associationPath.trim();

        String path;
        if (trimmed.contains(".")) {
            path = trimmed;
        } else {
            path = rootAlias + "." + trimmed;
        }

        String joinAlias;
        if (aliasOverride != null && !aliasOverride.isBlank()) {
            joinAlias = aliasOverride.trim();
        } else {
            int dot = trimmed.lastIndexOf('.');
            joinAlias = (dot >= 0 ? trimmed.substring(dot + 1) : trimmed);
        }

        jpql.append(joinType)
            .append(" ")
            .append(path)
            .append(" ")
            .append(joinAlias)
            .append(" ");

        return this;
    }

    public QueryBuilder where(String campo, Condicao condicao, Object... valores) {
        if (!hasWhere) {
            jpql.append("WHERE ");
            hasWhere = true;
        } else {
            jpql.append("AND ");
        }

        String f = qualifyField(campo);

        switch (condicao) {
            case BETWEEN -> {
                if (valores == null || valores.length != 2) {
                    throw new IllegalArgumentException("BETWEEN exige exatamente 2 valores");
                }
                jpql.append(f)
                    .append(" BETWEEN ?").append(params.size() + 1)
                    .append(" AND ?").append(params.size() + 2)
                    .append(" ");
                params.add(valores[0]);
                params.add(valores[1]);
            }
            case IN -> {
                if (valores == null || valores.length == 0) {
                    jpql.append("1 = 0 ");
                } else {
                    StringBuilder inBuilder = new StringBuilder();
                    inBuilder.append(f).append(" IN (");
                    for (int i = 0; i < valores.length; i++) {
                        if (i > 0) inBuilder.append(", ");
                        inBuilder.append("?").append(params.size() + 1 + i);
                    }
                    inBuilder.append(") ");
                    jpql.append(inBuilder);
                    params.addAll(Arrays.asList(valores));
                }
            }
            default -> {
                if (valores == null || valores.length == 0) {
                    throw new IllegalArgumentException("Condicao " + condicao + " exige pelo menos 1 valor");
                }
                jpql.append(f)
                    .append(" ")
                    .append(condicao.getOperador())
                    .append(" ?")
                    .append(params.size() + 1)
                    .append(" ");
                params.add(valores[0]);
            }
        }
        return this;
    }

    public QueryBuilder orderBy(String campo, boolean asc) {
        String f = qualifyField(campo);
        jpql.append("ORDER BY ")
            .append(f)
            .append(asc ? " ASC " : " DESC ");
        return this;
    }

    public QueryBuilder limit(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("limit deve ser maior que zero");
        }
        maxResults = max;
        return this;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <T> List<T> list() {
        Query query;

        if (projection && entityClass != null) {
            query = entityManager.createQuery(jpql.toString());
        } else if (entityClass != null && !projection) {
            query = entityManager.createQuery(jpql.toString(), entityClass);
        } else {
            query = entityManager.createQuery(jpql.toString());
        }

        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        DevSqlLogger.logSql(jpql.toString(), params);

        if (projection && entityClass != null) {
            List<?> raw = query.getResultList();
            return (List<T>) mapToEntities(raw);
        }

        return (List<T>) query.getResultList();
    }


    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <T> T one() {
        Query query;

        if (projection && entityClass != null) {
            query = entityManager.createQuery(jpql.toString());
        } else if (entityClass != null && !projection) {
            query = entityManager.createQuery(jpql.toString(), entityClass);
        } else {
            query = entityManager.createQuery(jpql.toString());
        }

        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }	

        DevSqlLogger.logSql(jpql.toString(), params);

        if (projection && entityClass != null) {
            Object row = query.getSingleResult();
            return (T) mapSingleRowToEntity(row);
        }

        return (T) query.getSingleResult();
    }


    @Transactional(readOnly = true)
    public <T> List<T> list(Class<T> resultClass) {
        Query query = entityManager.createQuery(jpql.toString());

        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<T> result = (List<T>) query.getResultList();
        return result;
    }

    @Transactional(readOnly = true)
    public <T> T one(Class<T> resultClass) {
        Query query = entityManager.createQuery(jpql.toString());

        if (maxResults != null) {
            query.setMaxResults(maxResults);
        }

        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
        @SuppressWarnings("unchecked")
        T result = (T) query.getSingleResult();
        return result;
    }

    private String qualifyField(String campo) {
        if (campo == null || campo.isBlank()) {
            throw new IllegalArgumentException("Campo JPQL não pode ser nulo/vazio");
        }

        String trimmed = campo.trim();

        if (trimmed.contains(".")) {
            return trimmed;
        }

        return rootAlias + "." + trimmed;
    }

    private void validateSelectedFieldsAgainstEntity() {
        if (entityClass == null) {
            return;
        }

        for (String raw : selectedRawFields) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String trimmed = raw.trim();
            String aliasPart = null;
            String fieldPart = trimmed;

            int dotIndex = trimmed.indexOf('.');
            if (dotIndex >= 0) {
                aliasPart = trimmed.substring(0, dotIndex);
                fieldPart = trimmed.substring(dotIndex + 1);
            }

            if (aliasPart != null && !aliasPart.equals(rootAlias)) {
                continue;
            }

            if (!fieldExistsInEntity(entityClass, fieldPart)) {
                log.warn("Campo '{}' não existe na entidade {} (select()).",
                        fieldPart, entityClass.getSimpleName());
            }
        }
    }

    private boolean fieldExistsInEntity(Class<?> clazz, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String target = fieldName.trim();

        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals(target)) {
                return true;
            }
        }
        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            for (Field f : superClass.getDeclaredFields()) {
                if (f.getName().equals(target)) {
                    return true;
                }
            }
            superClass = superClass.getSuperclass();
        }
        return false;
    }

    private List<?> mapToEntities(List<?> rawRows) {
        List<Object> result = new ArrayList<>();
        if (rawRows == null || rawRows.isEmpty()) {
            return result;
        }

        for (Object rowObj : rawRows) {
            Object entity = mapSingleRowToEntity(rowObj);
            result.add(entity);
        }

        return result;
    }

    private Object mapSingleRowToEntity(Object rowObj) {
        if (entityClass == null) {
            throw new IllegalStateException("entityClass é nula; não é possível mapear projeção para entidade.");
        }

        Object[] row;
        if (rowObj instanceof Object[]) {
            row = (Object[]) rowObj;
        } else {
            row = new Object[]{rowObj};
        }

        try {
            Object entity = entityClass.getDeclaredConstructor().newInstance();

            for (int i = 0; i < selectedRawFields.size() && i < row.length; i++) {
                String raw = selectedRawFields.get(i);
                if (raw == null || raw.isBlank()) {
                    continue;
                }

                String trimmed = raw.trim();
                String fieldName = trimmed;

                int dotIndex = trimmed.lastIndexOf('.');
                if (dotIndex >= 0) {
                    fieldName = trimmed.substring(dotIndex + 1);
                }

                Field field = findFieldInHierarchy(entityClass, fieldName);
                if (field == null) {
                    log.warn("Campo '{}' não encontrado na entidade {} ao mapear projeção.",
                            fieldName, entityClass.getSimpleName());
                    continue;
                }

                field.setAccessible(true);
                field.set(entity, row[i]);
            }

            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao instanciar entidade " + entityClass.getSimpleName()
                    + " a partir da projeção", e);
        }
    }

    private Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }
        String target = fieldName.trim();

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (f.getName().equals(target)) {
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public String build() {
        return jpql.toString().trim();
    }

    @Override
    public String toString() {
        return build();
    }
    
    public <T> T id(Number id) {
        if (id == null) {
            throw new IllegalArgumentException("id não pode ser nulo");
        }

        if (entityClass == null) {
            throw new IllegalStateException("Para usar id(), chame from(AlgumaEntidade.class) antes.");
        }

        if (projection) {
            throw new IllegalStateException("id() não deve ser usado com select(...) de campos específicos.");
        }

        where("id", Condicao.EQUAL, id);

        @SuppressWarnings("unchecked")
        T result = (T) one(); // reutiliza o fluxo já existente do one()

        return result;
    }
}