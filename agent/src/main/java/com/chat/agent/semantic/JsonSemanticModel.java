package com.chat.agent.semantic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * A semantic model read straight from a JSON file in resources/semantic/. Joins are worked
 * out from the table aliases used in each expression, so a definition never lists its own joins.
 */
@Getter
@Setter
public class JsonSemanticModel implements SemanticModel {

    private static final Pattern ALIAS = Pattern.compile("\\b([a-z_][a-z_0-9]*)\\s*\\.");
    private static final int MAX_METRIC_NESTING = 3;

    private String dataset;
    private String description;
    private String baseTable;

    /** alias -> table. Declare a table before any table that reaches through it. */
    private Map<String, SemanticTable> tables = new LinkedHashMap<>();

    private Map<String, SemanticField> metrics = new LinkedHashMap<>();
    private Map<String, SemanticField> dimensions = new LinkedHashMap<>();
    private Map<String, String> grains = new LinkedHashMap<>();
    private Map<String, Set<String>> cannotGroupBy = new LinkedHashMap<>();

    @Override
    public String metricSql(String metric) {
        return metricSql(metric, 0);
    }

    private String metricSql(String metric, int depth) {
        SemanticField field = metrics.get(metric);
        if (field == null) {
            throw new IllegalStateException("Metric '" + metric + "' is not defined in dataset " + dataset);
        }
        if (depth > MAX_METRIC_NESTING) {
            throw new IllegalStateException("Metric '" + metric + "' is nested too deeply; check for a loop");
        }
        return switch (typeOf(field)) {
            case "sum" -> "SUM(" + field.getSql() + ")";
            case "count" -> "COUNT(" + field.getSql() + ")";
            case "count_distinct" -> "COUNT(DISTINCT " + field.getSql() + ")";
            case "min" -> "MIN(" + field.getSql() + ")";
            case "max" -> "MAX(" + field.getSql() + ")";
            case "average" -> "AVG(" + field.getSql() + ")";
            // NULLIF turns a zero denominator into NULL instead of a divide-by-zero error
            case "ratio" -> "(" + metricSql(field.getNumerator(), depth + 1) + ")"
                    + " / NULLIF(" + metricSql(field.getDenominator(), depth + 1) + ", 0)";
            default -> throw new IllegalStateException(
                    "Unknown metric type '" + field.getType() + "' on metric '" + metric + "'");
        };
    }

    @Override
    public boolean isAdditive(String metric) {
        String type = typeOf(metrics.get(metric));
        return "sum".equals(type) || "count".equals(type);
    }

    private String typeOf(SemanticField field) {
        return field.getType() == null ? "sum" : field.getType();
    }

    @Override
    public String dateDimension() {
        return dimensions.entrySet().stream()
                .filter(entry -> "date".equals(entry.getValue().getType()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean cannotGroup(String metric, String dimension) {
        return cannotGroupBy.getOrDefault(metric, Set.of()).contains(dimension);
    }

    @Override
    public List<String> joinsFor(Collection<String> sqlExpressions) {
        Set<String> needed = neededAliases(sqlExpressions);
        List<String> clauses = new ArrayList<>();
        tables.forEach((alias, table) -> {
            if (needed.contains(alias)) {
                clauses.add(table.getJoin());
            }
        });
        return clauses;
    }

    @Override
    public long multiplyingJoins(Collection<String> sqlExpressions) {
        return neededAliases(sqlExpressions).stream()
                .filter(alias -> tables.get(alias).isMultiplying())
                .count();
    }

    private Set<String> neededAliases(Collection<String> sqlExpressions) {
        Set<String> needed = new LinkedHashSet<>();
        for (String expression : sqlExpressions) {
            collectAliases(expression, needed);
        }
        // a join can reach through another table (products is reached through order_items)
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String alias : new ArrayList<>(needed)) {
                changed |= collectAliases(tables.get(alias).getJoin(), needed);
            }
        }
        return needed;
    }

    private boolean collectAliases(String sql, Set<String> into) {
        boolean added = false;
        Matcher matcher = ALIAS.matcher(sql);
        while (matcher.find()) {
            if (tables.containsKey(matcher.group(1))) {
                added |= into.add(matcher.group(1));
            }
        }
        return added;
    }

    @Override
    public String describe() {
        StringBuilder text = new StringBuilder("METRICS (what can be measured):\n");
        metrics.forEach((name, field) -> text.append("- ").append(name).append(": ")
                .append(field.getDescription()).append("\n"));
        text.append("\nDIMENSIONS (what can be grouped or filtered by):\n");
        dimensions.forEach((name, field) -> text.append("- ").append(name).append(": ")
                .append(field.getDescription())
                .append(field.isExplain() ? " [can explain a change]" : "")
                .append("\n"));
        return text.toString();
    }
}
