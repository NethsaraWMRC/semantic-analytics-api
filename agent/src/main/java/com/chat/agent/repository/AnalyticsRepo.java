package com.chat.agent.repository;

import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepo {

    private final JdbcTemplate jdbc;

    public AnalyticsRepo(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(5);
    }

    public List<Map<String, Object>> query(String sql, List<Object> params) {
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
}
