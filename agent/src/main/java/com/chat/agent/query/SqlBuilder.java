package com.chat.agent.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.chat.agent.query.DescriptiveQuery.Dimension;
import com.chat.agent.query.DescriptiveQuery.Filter;
import com.chat.agent.query.DescriptiveQuery.Sort;
import com.chat.agent.semantic.SemanticLayer;

/** Turns an already-validated query into parameterized SQL. Names come from SemanticLayer only; values are bound as ?. */
@Component
public class SqlBuilder {

    public record BuiltSql(String sql, List<Object> params) {}

    public BuiltSql build(DescriptiveQuery q, int limit) {
        List<String> select = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        List<String> where = new ArrayList<>();
        List<String> orderBy = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        Set<String> joins = new HashSet<>();

        for (Dimension d : orEmpty(q.dimensions())) {
            SemanticLayer.Dimension def = SemanticLayer.DIMENSIONS.get(d.field());
            String expr = d.grain() == null
                    ? def.column()
                    : SemanticLayer.GRAINS.get(d.grain()).formatted(def.column());
            select.add(expr + " AS " + d.field());
            groupBy.add(expr);
            joins.addAll(def.joins());
        }

        for (String m : q.metrics()) {
            SemanticLayer.Metric def = SemanticLayer.METRICS.get(m);
            select.add(def.expr() + " AS " + m);
            joins.addAll(def.joins());
        }

        for (Filter f : orEmpty(q.filters())) {
            SemanticLayer.Dimension def = SemanticLayer.DIMENSIONS.get(f.field());
            joins.addAll(def.joins());
            String col = def.column();
            switch (f.operator()) {
                case "in" -> {
                    List<?> values = (List<?>) f.value();
                    where.add(col + " IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")");
                    params.addAll(values);
                }
                case "between" -> {
                    List<?> values = (List<?>) f.value();
                    where.add(col + " BETWEEN ? AND ?");
                    params.addAll(values);
                }
                case "=", "!=", ">", ">=", "<", "<=" -> {
                    where.add(col + " " + f.operator() + " ?");
                    params.add(f.value());
                }
                default -> throw new IllegalStateException("Unvalidated operator: " + f.operator());
            }
        }

        for (Sort s : orEmpty(q.sort())) {
            orderBy.add(s.field() + ("DESC".equalsIgnoreCase(s.direction()) ? " DESC" : " ASC"));
        }

        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", select));
        sql.append(" FROM orders o");
        for (String join : resolveJoins(joins)) {
            sql.append(" ").append(join);
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }
        if (!groupBy.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupBy));
        }
        if (!orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orderBy));
        }
        sql.append(" LIMIT ?");
        params.add(limit);

        return new BuiltSql(sql.toString(), params);
    }

    private List<String> resolveJoins(Set<String> needed) {
        Set<String> all = new HashSet<>(needed);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String name : new ArrayList<>(all)) {
                changed |= all.addAll(SemanticLayer.JOINS.get(name).requires());
            }
        }
        List<String> clauses = new ArrayList<>();
        for (Map.Entry<String, SemanticLayer.Join> e : SemanticLayer.JOINS.entrySet()) {
            if (all.contains(e.getKey())) {
                clauses.add(e.getValue().clause());
            }
        }
        return clauses;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
