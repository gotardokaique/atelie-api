package com.gestao.api.dev;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DevSqlLogger {

    private static final Logger log = LoggerFactory.getLogger(DevSqlLogger.class);

    private static boolean developerMode = false;

    // pacote base das entidades
    private static final String ENTITY_BASE_PACKAGE = "com.api.cotacao.entity";

    // cache de mapeamentos por nome de entidade JPQL (ex: "CotacaoCafeEntity")
    private static final Map<String, EntityMapping> MAPPING_CACHE = new ConcurrentHashMap<>();

    // ANSI colors
    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";

    private DevSqlLogger() {}

    public static void setDeveloperMode(boolean enabled) {
        developerMode = enabled;
    }

    public static void logSql(String jpql, List<Object> params) {
        if (!developerMode || !log.isInfoEnabled() || jpql == null) {
            return;
        }

        String caller = resolveCaller();

        // 1) JPQL com parâmetros embutidos (sem formatar ainda)
        String bound = bindParams(jpql, params);

        // 2) SELECT (visão dev) = JPQL formatado
        String jpqlPretty = formatSqlPretty(bound);

        // 3) SELECT-BD (se der pra mapear tabela/colunas)
        String sqlDbPretty = null;
        String sqlDbRaw = toDatabaseNames(bound);
        if (sqlDbRaw != null) {
            sqlDbPretty = formatSqlPretty(sqlDbRaw);
        }

        String header        = GREEN  + "[DEV][SQL]"   + RESET;
        String callerLabel   = GREEN  + "LOCAL      "  + RESET;
        String selectLabel   = GREEN  + "SELECT     "  + RESET;
        String selectDbLabel = GREEN  + "SELECT-BD  "  + RESET;
        String paramsLabel   = GREEN  + "PARAMETROS "  + RESET;

        String selectColored  = YELLOW + "\n" + jpqlPretty  + RESET;
        String paramsColored  = CYAN   + String.valueOf(params) + RESET;

        if (sqlDbPretty != null) {
            String selectDbColored = YELLOW + "\n" + sqlDbPretty + RESET;
            log.info("""
                    {}
                      {}: {}
                      {}: {}
                      {}: {}
                      {}: {}
                    """,
                    header,
                    callerLabel,   caller,
                    selectLabel,   selectColored,
                    selectDbLabel, selectDbColored,
                    paramsLabel,   paramsColored
            );
        } else {
            // sem SELECT-BD
            log.info("""
                    {}
                      {}: {}
                      {}: {}
                      {}: {}
                    """,
                    header,
                    callerLabel,  caller,
                    selectLabel,  selectColored,
                    paramsLabel,  paramsColored
            );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String resolveCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        for (StackTraceElement e : stack) {
            String cn = e.getClassName();

            if (cn.equals(DevSqlLogger.class.getName())) continue;
            if (cn.contains("QueryBuilder")) continue;
            if (cn.contains("DAOController")) continue;
            if (cn.startsWith("java.")) continue;
            if (cn.startsWith("jakarta.")) continue;
            if (cn.startsWith("org.springframework")) continue;
            if (cn.startsWith("org.hibernate")) continue;

            return cn + "#" + e.getMethodName() + ":" + e.getLineNumber();
        }
        return "unknown";
    }

    // Embute os parâmetros na JPQL (sem formatar ainda)
    private static String bindParams(String jpql, List<Object> params) {
        if (jpql == null) {
            return null;
        }
        if (params == null || params.isEmpty()) {
            return jpql;
        }

        String resolved = jpql;

        // de trás pra frente pra não zoar ?10 com ?1
        for (int i = params.size(); i >= 1; i--) {
            Object value = params.get(i - 1);
            String rendered;

            if (value == null) {
                rendered = "null";
            } else if (value instanceof String) {
                rendered = "'" + value + "'";
            } else if (value instanceof LocalDate
                    || value instanceof LocalDateTime
                    || value instanceof LocalTime) {
                rendered = "'" + value.toString() + "'";
            } else {
                rendered = value.toString();
            }

            resolved = resolved.replace("?" + i, rendered);
        }

        return resolved;
    }

    // Formata SQL/JPQL em múltiplas linhas
    private static String formatSqlPretty(String sqlRaw) {
        if (sqlRaw == null || sqlRaw.isBlank()) {
            return sqlRaw;
        }

        String s = sqlRaw.replaceAll("\\s+", " ").trim();

        s = s.replace(" SELECT ",     "\nSELECT ");
        s = s.replace(" FROM ",       "\n  FROM ");
        s = s.replace(" WHERE ",      "\n  WHERE ");
        s = s.replace(" INNER JOIN ", "\n  INNER JOIN ");
        s = s.replace(" LEFT JOIN ",  "\n  LEFT JOIN ");
        s = s.replace(" JOIN ",       "\n  JOIN ");
        s = s.replace(" GROUP BY ",   "\n  GROUP BY ");
        s = s.replace(" ORDER BY ",   "\n  ORDER BY ");
        s = s.replace(" AND ",        "\n    AND ");
        s = s.replace(" OR ",         "\n    OR ");

        return s.strip();
    }

    // Converte entity/fields -> tabela/colunas usando @Table/@Column
    // Se não conseguir mapear, retorna null (não mostra SELECT-BD)
    private static String toDatabaseNames(String rawWithParams) {
        if (rawWithParams == null) {
            return null;
        }

        String sql = rawWithParams;

        String upper = sql.toUpperCase();
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) {
            return null;
        }

        String afterFrom = sql.substring(fromIdx + " FROM ".length()).trim();
        String[] parts = afterFrom.split("\\s+");
        if (parts.length < 2) {
            return null;
        }

        String entityName = parts[0];  // ex: CotacaoCafeEntity
        String alias      = parts[1];  // ex: c

        EntityMapping mapping = resolveMapping(entityName);
        if (mapping == null) {
            return null; 
        }

        sql = sql.replace(
                "FROM " + entityName + " " + alias,
                "FROM " + mapping.tableName + " " + alias
        );

        for (Map.Entry<String, String> entry : mapping.fieldToColumn.entrySet()) {
            String fieldRef  = alias + "." + entry.getKey();
            String columnRef = alias + "." + entry.getValue();
            sql = sql.replace(fieldRef, columnRef);
        }

        return sql;
    }

    private static EntityMapping resolveMapping(String entitySimpleName) {
        return MAPPING_CACHE.computeIfAbsent(entitySimpleName, DevSqlLogger::buildMappingForEntity);
    }

    private static EntityMapping buildMappingForEntity(String entitySimpleName) {
        try {
            String fqcn = ENTITY_BASE_PACKAGE + "." + entitySimpleName;
            Class<?> clazz = Class.forName(fqcn);

            String tableName;
            Table table = clazz.getAnnotation(Table.class);
            if (table != null && table.name() != null && !table.name().isBlank()) {
                tableName = table.name();
            } else {
                tableName = toSnakeCase(entitySimpleName);
            }

            Map<String, String> fieldToColumn = new HashMap<>();

            for (Field f : clazz.getDeclaredFields()) {
                String fieldName = f.getName();

                Column col = f.getAnnotation(Column.class);
                String colName;
                if (col != null && col.name() != null && !col.name().isBlank()) {
                    colName = normalizeColumnName(col.name());
                } else {
                    colName = toSnakeCase(fieldName);
                }

                fieldToColumn.put(fieldName, colName);
            }

            return new EntityMapping(entitySimpleName, tableName, fieldToColumn);
        } catch (ClassNotFoundException e) {
            log.debug("[DEV][SQL] Não foi possível resolver entidade '{}'", entitySimpleName);
            return null;
        } catch (Exception e) {
            log.debug("[DEV][SQL] Erro ao montar mapping para entidade '{}': {}", entitySimpleName, e.getMessage());
            return null;
        }
    }

    private static String toSnakeCase(String s) {
        if (s == null || s.isBlank()) return s;
        StringBuilder sb = new StringBuilder();
        char[] arr = s.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char c = arr[i];
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private record EntityMapping(String entityName,
                                 String tableName,
                                 Map<String, String> fieldToColumn) {}
    
    private static String normalizeColumnName(String name) {
        if (name == null || name.isBlank()) return name;

        boolean anyUpper = false;
        boolean anyLower = false;

        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) anyUpper = true;
            if (Character.isLowerCase(c)) anyLower = true;
            if (anyUpper && anyLower) break;
        }

        if (anyUpper && anyLower) {
            return toSnakeCase(name);
        }

        return name;
    }

}