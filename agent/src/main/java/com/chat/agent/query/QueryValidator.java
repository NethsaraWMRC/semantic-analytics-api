package com.chat.agent.query;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.chat.agent.query.DescriptiveQuery.Dimension;
import com.chat.agent.query.DescriptiveQuery.Filter;
import com.chat.agent.query.DescriptiveQuery.Sort;
import com.chat.agent.semantic.SemanticLayer;

@Component
public class QueryValidator {

    public static final int MAX_LIMIT = 100;
    private static final int MAX_DIMENSIONS = 3;
    private static final int MAX_IN_VALUES = 50;
    private static final Set<String> OPERATORS = Set.of("=", "!=", ">", ">=", "<", "<=", "in", "between");

    public void validate(DescriptiveQuery q) {
        if (!"sales".equals(q.dataset())) {
            throw fail("Unknown dataset '%s'. Only 'sales' is available.", q.dataset());
        }

        List<String> metrics = q.metrics() == null ? List.of() : q.metrics();
        if (metrics.isEmpty()) {
            throw fail("At least one metric is required. Available metrics: %s", SemanticLayer.METRICS.keySet());
        }
        for (String m : metrics) {
            if (!SemanticLayer.METRICS.containsKey(m)) {
                throw fail("Unknown metric '%s'. Available metrics: %s", m, SemanticLayer.METRICS.keySet());
            }
        }

        List<Dimension> dimensions = q.dimensions() == null ? List.of() : q.dimensions();
        if (dimensions.size() > MAX_DIMENSIONS) {
            throw fail("At most %d dimensions are allowed.", MAX_DIMENSIONS);
        }
        Set<String> selected = new HashSet<>(metrics);
        for (Dimension d : dimensions) {
            if (d == null || d.field() == null || !SemanticLayer.DIMENSIONS.containsKey(d.field())) {
                throw fail("Unknown dimension '%s'. Available dimensions: %s",
                        d == null ? null : d.field(), SemanticLayer.DIMENSIONS.keySet());
            }
            if (d.grain() != null) {
                if (!"order_date".equals(d.field())) {
                    throw fail("Grain is only supported on 'order_date', not '%s'.", d.field());
                }
                if (!SemanticLayer.GRAINS.containsKey(d.grain())) {
                    throw fail("Unknown grain '%s'. Available grains: %s", d.grain(), SemanticLayer.GRAINS.keySet());
                }
            }
            selected.add(d.field());
            for (String m : metrics) {
                if (SemanticLayer.METRICS.get(m).incompatibleDimensions().contains(d.field())) {
                    throw fail("Metric '%s' cannot be broken down by '%s': the result would be misleading.",
                            m, d.field());
                }
            }
        }

        List<Filter> filters = q.filters() == null ? List.of() : q.filters();
        for (Filter f : filters) {
            validateFilter(f);
        }

        List<Sort> sorts = q.sort() == null ? List.of() : q.sort();
        for (Sort s : sorts) {
            if (s == null || s.field() == null || !selected.contains(s.field())) {
                throw fail("Cannot sort by '%s'. Sort by one of the selected metrics or dimensions: %s",
                        s == null ? null : s.field(), selected);
            }
            if (s.direction() != null && !"ASC".equalsIgnoreCase(s.direction())
                    && !"DESC".equalsIgnoreCase(s.direction())) {
                throw fail("Sort direction must be ASC or DESC, got '%s'.", s.direction());
            }
        }

        if (q.limit() != null && q.limit() < 1) {
            throw fail("Limit must be at least 1.");
        }
    }

    private void validateFilter(Filter f) {
        if (f == null || f.field() == null || !SemanticLayer.DIMENSIONS.containsKey(f.field())) {
            throw fail("Cannot filter on '%s'. Filterable fields: %s",
                    f == null ? null : f.field(), SemanticLayer.DIMENSIONS.keySet());
        }
        if (f.operator() == null || !OPERATORS.contains(f.operator())) {
            throw fail("Operator '%s' is not allowed. Allowed operators: %s", f.operator(), OPERATORS);
        }

        List<Object> values = new ArrayList<>();
        switch (f.operator()) {
            case "in" -> {
                if (!(f.value() instanceof List<?> list) || list.isEmpty() || list.size() > MAX_IN_VALUES) {
                    throw fail("Operator 'in' needs a list of 1 to %d values.", MAX_IN_VALUES);
                }
                values.addAll(list);
            }
            case "between" -> {
                if (!(f.value() instanceof List<?> list) || list.size() != 2) {
                    throw fail("Operator 'between' needs a list of exactly two values.");
                }
                values.addAll(list);
            }
            default -> values.add(f.value());
        }

        for (Object v : values) {
            if (!(v instanceof String || v instanceof Number || v instanceof Boolean)) {
                throw fail("Filter on '%s' has an invalid value.", f.field());
            }
            if ("order_date".equals(f.field())) {
                try {
                    LocalDate.parse(String.valueOf(v));
                } catch (DateTimeParseException e) {
                    throw fail("Filter on 'order_date' needs dates like 2026-08-01, got '%s'.", v);
                }
            }
        }
    }

    private static InvalidQueryException fail(String message, Object... args) {
        return new InvalidQueryException(String.format(message, args));
    }
}
