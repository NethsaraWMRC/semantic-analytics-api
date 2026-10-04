package com.chat.agent.query;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.dto.MetricFilter;
import com.chat.agent.dto.QueryDimension;
import com.chat.agent.dto.QueryFilter;
import com.chat.agent.dto.QuerySort;
import com.chat.agent.dto.TopPerGroup;
import com.chat.agent.semantic.SemanticModel;

/** Rejects anything the semantic model does not allow, before a single line of SQL is built. */
@Component
public class QueryValidator {

    /** rows returned when the request does not ask for a specific number */
    public static final int MAX_LIMIT = 100;

    /** the most rows an explicit request can ask for, so "show all" is possible but bounded */
    public static final int HARD_MAX_LIMIT = 1000;
    private static final int MAX_DIMENSIONS = 3;
    private static final int MAX_IN_VALUES = 50;
    private static final int MAX_TOP_PER_GROUP = 50;
    private static final Set<String> OPERATORS = Set.of("=", "!=", ">", ">=", "<", "<=", "in", "between");
    private static final Set<String> NUMERIC_OPERATORS = Set.of("=", "!=", ">", ">=", "<", "<=");

    public void validate(DescriptiveQuery query, SemanticModel model) {
        Set<String> dimensionNames = new LinkedHashSet<>();
        Set<String> sortable = new HashSet<>();

        if (query.getMetrics().isEmpty()) {
            throw fail("At least one metric is required. Available metrics: %s", model.getMetrics().keySet());
        }
        for (String metric : query.getMetrics()) {
            if (!model.getMetrics().containsKey(metric)) {
                throw fail("Unknown metric '%s'. Available metrics: %s", metric, model.getMetrics().keySet());
            }
            sortable.add(metric);
        }

        if (query.getDimensions().size() > MAX_DIMENSIONS) {
            throw fail("At most %d dimensions are allowed.", MAX_DIMENSIONS);
        }
        for (QueryDimension dimension : query.getDimensions()) {
            validateDimension(dimension, query.getMetrics(), model);
            dimensionNames.add(dimension.getField());
            sortable.add(dimension.getField());
        }

        for (QueryFilter filter : query.getFilters()) {
            validateFilter(filter, model);
        }

        for (MetricFilter metricFilter : query.getHaving()) {
            validateMetricFilter(metricFilter, model);
        }

        for (QuerySort sort : query.getSort()) {
            validateSort(sort, sortable);
        }

        if (query.isPercentOfTotal()) {
            validatePercentOfTotal(query, model);
        }

        if (query.getTopPerGroup() != null) {
            validateTopPerGroup(query.getTopPerGroup(), query, dimensionNames);
        }

        if (query.getCompareTo() != null && !"previous_period".equals(query.getCompareTo())) {
            throw fail("compareTo only supports 'previous_period', got '%s'.", query.getCompareTo());
        }

        if (query.getLimit() != null && query.getLimit() < 1) {
            throw fail("Limit must be at least 1.");
        }
        if (query.getLimit() != null && query.getLimit() > HARD_MAX_LIMIT) {
            throw fail("At most %d rows can be returned at once.", HARD_MAX_LIMIT);
        }
    }

    private void validateDimension(QueryDimension dimension, List<String> metrics, SemanticModel model) {
        if (dimension == null) {
            throw fail("A dimension entry is empty.");
        }
        String field = dimension.getField();
        if (field == null || !model.getDimensions().containsKey(field)) {
            throw fail("Unknown dimension '%s'. Available dimensions: %s", field, model.getDimensions().keySet());
        }
        if (dimension.getGrain() != null && !model.getGrains().containsKey(dimension.getGrain())) {
            throw fail("Unknown grain '%s'. Available grains: %s", dimension.getGrain(), model.getGrains().keySet());
        }
        for (String metric : metrics) {
            if (model.cannotGroup(metric, field)) {
                throw fail("Metric '%s' cannot be broken down by '%s': the result would be misleading.", metric, field);
            }
        }
    }

    private void validateFilter(QueryFilter filter, SemanticModel model) {
        if (filter == null) {
            throw fail("A filter entry is empty.");
        }
        String field = filter.getField();
        if (field == null || !model.getDimensions().containsKey(field)) {
            throw fail("Cannot filter on '%s'. Filterable fields: %s", field, model.getDimensions().keySet());
        }
        if (filter.getOperator() == null || !OPERATORS.contains(filter.getOperator())) {
            throw fail("Operator '%s' is not allowed. Allowed operators: %s", filter.getOperator(), OPERATORS);
        }

        List<Object> values = new ArrayList<>();
        switch (filter.getOperator()) {
            case "in" -> {
                if (!(filter.getValue() instanceof List<?> list) || list.isEmpty() || list.size() > MAX_IN_VALUES) {
                    throw fail("Operator 'in' needs a list of 1 to %d values.", MAX_IN_VALUES);
                }
                values.addAll(list);
            }
            case "between" -> {
                if (!(filter.getValue() instanceof List<?> list) || list.size() != 2) {
                    throw fail("Operator 'between' needs a list of exactly two values.");
                }
                values.addAll(list);
            }
            default -> values.add(filter.getValue());
        }

        boolean isDate = "date".equals(model.getDimensions().get(field).getType());
        for (Object value : values) {
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw fail("Filter on '%s' has an invalid value.", field);
            }
            if (isDate) {
                try {
                    LocalDate.parse(String.valueOf(value));
                } catch (DateTimeParseException e) {
                    throw fail("Filter on '%s' needs a date like 2026-08-01 or a period name such as %s, got '%s'.",
                            field, PeriodResolver.PERIODS, value);
                }
            }
        }
    }

    private void validateMetricFilter(MetricFilter metricFilter, SemanticModel model) {
        if (metricFilter == null || metricFilter.getMetric() == null
                || !model.getMetrics().containsKey(metricFilter.getMetric())) {
            throw fail("Cannot filter on total '%s'. Available metrics: %s",
                    metricFilter == null ? null : metricFilter.getMetric(), model.getMetrics().keySet());
        }
        if (metricFilter.getOperator() == null || !NUMERIC_OPERATORS.contains(metricFilter.getOperator())) {
            throw fail("Operator '%s' is not allowed on a total. Allowed operators: %s",
                    metricFilter.getOperator(), NUMERIC_OPERATORS);
        }
        if (metricFilter.getValue() == null) {
            throw fail("Filter on total '%s' needs a number to compare against.", metricFilter.getMetric());
        }
    }

    private void validateSort(QuerySort sort, Set<String> sortable) {
        if (sort == null) {
            throw fail("A sort entry is empty.");
        }
        String field = sort.getField();
        if (field == null || !sortable.contains(field)) {
            throw fail("Cannot sort by '%s'. Sort by one of the selected metrics or dimensions: %s", field, sortable);
        }
        String direction = sort.getDirection();
        if (direction != null && !"ASC".equalsIgnoreCase(direction) && !"DESC".equalsIgnoreCase(direction)) {
            throw fail("Sort direction must be ASC or DESC, got '%s'.", direction);
        }
    }

    private void validatePercentOfTotal(DescriptiveQuery query, SemanticModel model) {
        if (query.getDimensions().isEmpty()) {
            throw fail("percentOfTotal needs at least one dimension, otherwise every row is 100%%.");
        }
        for (String metric : query.getMetrics()) {
            if (!model.isAdditive(metric)) {
                throw fail("Metric '%s' cannot be shown as a share of the total, because its values do not add up "
                        + "across groups.", metric);
            }
        }
    }

    private void validateTopPerGroup(TopPerGroup topPerGroup, DescriptiveQuery query, Set<String> dimensionNames) {
        if (query.getDimensions().size() < 2) {
            throw fail("topPerGroup needs two dimensions: the group to split by, and what to rank inside it.");
        }
        if (topPerGroup.getWithin() == null || !dimensionNames.contains(topPerGroup.getWithin())) {
            throw fail("topPerGroup.within must be one of this query's dimensions: %s", dimensionNames);
        }
        if (topPerGroup.getTop() == null || topPerGroup.getTop() < 1 || topPerGroup.getTop() > MAX_TOP_PER_GROUP) {
            throw fail("topPerGroup.top must be between 1 and %d.", MAX_TOP_PER_GROUP);
        }
    }

    private static InvalidQueryException fail(String message, Object... args) {
        return new InvalidQueryException(String.format(message, args));
    }
}
