package com.chat.agent.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.dto.MetricFilter;
import com.chat.agent.dto.QueryDimension;
import com.chat.agent.dto.QueryFilter;
import com.chat.agent.dto.QuerySort;
import com.chat.agent.dto.TopPerGroup;
import com.chat.agent.semantic.SemanticModel;

/**
 * Turns an already-validated query into parameterized SQL. Table and column names only ever
 * come from the semantic model; every value from the request is bound as a ? parameter.
 *
 * Only the joins the query actually needs are added, so a question that touches one table
 * reads one table.
 */
@Component
public class SqlBuilder {

    public static final String RANK_COLUMN = "rank_in_group";

    public BuiltSql build(DescriptiveQuery query, SemanticModel model, int limit) {
        List<String> select = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        List<String> where = new ArrayList<>();
        List<String> having = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<String> usedSql = new ArrayList<>();
        Map<String, String> dimensionSql = new LinkedHashMap<>();

        for (QueryDimension dimension : query.getDimensions()) {
            String column = model.getDimensions().get(dimension.getField()).getSql();
            usedSql.add(column);

            String expression = dimension.getGrain() == null
                    ? column
                    : model.getGrains().get(dimension.getGrain()).replace("{}", column);

            dimensionSql.put(dimension.getField(), expression);
            select.add(expression + " AS " + dimension.getField());
            groupBy.add(expression);
        }

        for (String metric : query.getMetrics()) {
            String expression = model.metricSql(metric);
            usedSql.add(expression);
            select.add(expression + " AS " + metric);

            if (query.isPercentOfTotal()) {
                // the window runs after grouping, so SUM(<aggregate>) OVER () is the grand total
                select.add("ROUND(100.0 * " + expression + " / NULLIF(SUM(" + expression + ") OVER (), 0), 2)"
                        + " AS " + metric + "_pct");
            }
        }

        // WHERE params come first because WHERE comes first in the statement
        for (QueryFilter filter : query.getFilters()) {
            String column = model.getDimensions().get(filter.getField()).getSql();
            usedSql.add(column);
            where.add(condition(column, filter, params));
        }

        for (MetricFilter metricFilter : query.getHaving()) {
            String expression = model.metricSql(metricFilter.getMetric());
            usedSql.add(expression);
            having.add(expression + " " + metricFilter.getOperator() + " ?");
            params.add(metricFilter.getValue());
        }

        TopPerGroup topPerGroup = query.getTopPerGroup();
        if (topPerGroup != null) {
            select.add("RANK() OVER (PARTITION BY " + dimensionSql.get(topPerGroup.getWithin())
                    + " ORDER BY " + rankOrder(query, model) + ") AS " + RANK_COLUMN);
        }

        // two row-multiplying joins in one statement would inflate every sum, so refuse instead
        if (model.multiplyingJoins(usedSql) > 1) {
            throw new InvalidQueryException("This combination would need two tables that each have many rows "
                    + "per order, which would multiply the totals. Please ask for these metrics separately.");
        }

        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", select));
        sql.append(" FROM ").append(model.getBaseTable());
        for (String join : model.joinsFor(usedSql)) {
            sql.append(" ").append(join);
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }
        if (!groupBy.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupBy));
        }
        if (!having.isEmpty()) {
            sql.append(" HAVING ").append(String.join(" AND ", having));
        }

        if (topPerGroup != null) {
            // the rank has to exist before it can be filtered, so the ranked query becomes a subquery
            sql = new StringBuilder("SELECT * FROM (").append(sql).append(") ranked")
                    .append(" WHERE ").append(RANK_COLUMN).append(" <= ?")
                    .append(" ORDER BY ").append(topPerGroup.getWithin()).append(", ").append(RANK_COLUMN);
            params.add(topPerGroup.getTop());
        } else {
            List<String> orderBy = new ArrayList<>();
            for (QuerySort sort : query.getSort()) {
                orderBy.add(sort.getField() + direction(sort.getDirection()));
            }
            if (!orderBy.isEmpty()) {
                sql.append(" ORDER BY ").append(String.join(", ", orderBy));
            }
        }

        sql.append(" LIMIT ?");
        params.add(limit);

        return new BuiltSql(sql.toString(), params);
    }

    private String condition(String column, QueryFilter filter, List<Object> params) {
        switch (filter.getOperator()) {
            case "in" -> {
                List<?> values = (List<?>) filter.getValue();
                params.addAll(values);
                return column + " IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")";
            }
            case "between" -> {
                List<?> values = (List<?>) filter.getValue();
                params.addAll(values);
                return column + " BETWEEN ? AND ?";
            }
            default -> {
                params.add(filter.getValue());
                return column + " " + filter.getOperator() + " ?";
            }
        }
    }

    /** Ranks by the sorted metric when there is one, otherwise by the first metric. */
    private String rankOrder(DescriptiveQuery query, SemanticModel model) {
        for (QuerySort sort : query.getSort()) {
            if (model.getMetrics().containsKey(sort.getField())) {
                return model.metricSql(sort.getField()) + direction(sort.getDirection());
            }
        }
        return model.metricSql(query.getMetrics().get(0)) + " DESC";
    }

    private String direction(String direction) {
        return "ASC".equalsIgnoreCase(direction) ? " ASC" : " DESC";
    }
}
