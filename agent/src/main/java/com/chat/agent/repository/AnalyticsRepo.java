package com.chat.agent.repository;

import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepo {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRepo.class);

    private final JdbcTemplate jdbc;

    public AnalyticsRepo(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(5);
    }

    public List<Map<String, Object>> query(String sql, List<Object> params) {
        log.info("analytics SQL: {} | params: {}", sql, params);
        try {
            return runQuery(sql, params);
        } catch (DataAccessException e) {
            log.error("analytics SQL failed: {} | params: {} | cause: {}", sql, params, rootMessage(e));
            throw e;
        }
    }

    /** distinct values of one dimension, used to match what the person typed. */
    public List<String> distinctValues(String sql) {
        return jdbc.queryForList(sql, String.class);
    }

    private List<Map<String, Object>> runQuery(String sql, List<Object> params) {
        return jdbc.query(sql, (rs, rowNum) -> {
            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                Object value = rs.getObject(i);
                if (value != null && !(value instanceof Number || value instanceof String || value instanceof Boolean)) {
                    value = value.toString();
                }
                row.put(meta.getColumnLabel(i), value);
            }
            return row;
        }, params.toArray());
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
