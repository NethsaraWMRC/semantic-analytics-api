package com.chat.agent.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chat.agent.dto.AnalyticsResult;
import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.dto.QueryDimension;
import com.chat.agent.dto.QueryFilter;
import com.chat.agent.query.BuiltSql;
import com.chat.agent.query.DataCoverage;
import com.chat.agent.query.DateRange;
import com.chat.agent.query.InvalidQueryException;
import com.chat.agent.query.PeriodResolver;
import com.chat.agent.query.QueryValidator;
import com.chat.agent.query.ValueResolver;
import com.chat.agent.query.SqlBuilder;
import com.chat.agent.repository.AnalyticsRepo;
import com.chat.agent.semantic.SemanticModel;
import com.chat.agent.semantic.SemanticRegistry;

/** Resolve periods -> validate -> build SQL -> run -> shape the result. No LLM involved. */
@Service
public class AnalyticsService {

    /** beyond this many filters, finding the guilty one costs more than it is worth */
    private static final int MAX_FILTERS_TO_DIAGNOSE = 4;

    private final QueryValidator validator;
    private final SqlBuilder builder;
    private final AnalyticsRepo repo;
    private final SemanticRegistry registry;
    private final ValueResolver valueResolver;
    private final DataCoverage coverage;
    private final String defaultDataset;

    public AnalyticsService(QueryValidator validator, SqlBuilder builder, AnalyticsRepo repo,
                            SemanticRegistry registry, ValueResolver valueResolver,
                            DataCoverage coverage,
                            @Value("${analytics.dataset}") String defaultDataset) {
        this.validator = validator;
        this.builder = builder;
        this.repo = repo;
        this.registry = registry;
        this.valueResolver = valueResolver;
        this.coverage = coverage;
        this.defaultDataset = defaultDataset;
    }

    public AnalyticsResult run(DescriptiveQuery query) {
        SemanticModel model = findModel(query.getDataset());

        // named periods become real dates here, never in the LLM
        DateRange period = PeriodResolver.expand(query, model, LocalDate.now());
        validator.validate(query, model);

        // match loose wording against the values that exist; may ask the person to choose
        List<String> resolverNotes = new ArrayList<>();
        valueResolver.resolve(query, model, resolverNotes);

        AnalyticsResult result = execute(query, model);
        result.getNotes().addAll(resolverNotes);

        // a cut-off list makes the visible rows unsafe to add up, so supply the real totals
        if (result.isTruncated()) {
            result.setGrandTotals(totalsWithoutGrouping(query, model));
        }

        // an empty answer is usually a filter that matches nothing, not a true zero
        if (result.getRowCount() == 0) {
            explainEmptyResult(query, model, period, result.getNotes());
        }

        notePartlyEmptyDimensions(result, query);

        if (query.getCompareTo() != null) {
            addComparison(result, query, model, period);
        }
        addNotes(result, query, model);
        return result;
    }

    private AnalyticsResult execute(DescriptiveQuery query, SemanticModel model) {
        int limit = query.getLimit() == null
                ? QueryValidator.MAX_LIMIT
                : Math.min(query.getLimit(), QueryValidator.HARD_MAX_LIMIT);

        // ask for one row more than we need, so we can tell whether more existed
        BuiltSql built = builder.build(query, model, limit + 1);
        List<Map<String, Object>> rows = repo.query(built.getSql(), built.getParams());

        boolean moreRowsExist = rows.size() > limit;
        if (moreRowsExist) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }

        // only call it truncated when a cap did the cutting, not when the user asked for a top N
        boolean serverCapped = query.getLimit() == null
                || query.getLimit() > QueryValidator.HARD_MAX_LIMIT;

        AnalyticsResult result = new AnalyticsResult();
        result.setColumns(columnsOf(query));
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setTruncated(moreRowsExist && serverCapped);
        return result;
    }

    /** The same metrics over the same filters, with no grouping: the honest totals. */
    private Map<String, Object> totalsWithoutGrouping(DescriptiveQuery query, SemanticModel model) {
        DescriptiveQuery totals = copyWithoutGrouping(query);
        List<Map<String, Object>> rows = execute(totals, model).getRows();
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /**
     * Works out why nothing came back. A date outside the data is the common case; otherwise one
     * filter is doing the eliminating, and naming it is far more use than "no rows".
     */
    private void explainEmptyResult(DescriptiveQuery query, SemanticModel model, DateRange period,
                                    List<String> notes) {
        // whenever a dated question finds nothing, say what the dataset actually holds: "no data
        // for that day" and "nothing sold that day" are different answers and must not be mixed up
        DateRange covered = coverage.of(model);
        if (period != null && covered != null) {
            boolean outside = period.getStart().isAfter(covered.getEndExclusive())
                    || period.getEndExclusive().isBefore(covered.getStart());
            notes.add("The " + model.getDataset() + " data runs from " + covered.getStart() + " to "
                    + covered.getEndExclusive().minusDays(1) + "."
                    + (outside ? " The question asked outside that range, so there is nothing to report"
                               + " rather than nothing having happened." : ""));
            if (outside) {
                return;
            }
        }

        List<QueryFilter> filters = query.getFilters();
        if (filters.size() < 2 || filters.size() > MAX_FILTERS_TO_DIAGNOSE) {
            return;
        }

        for (QueryFilter suspect : filters) {
            DescriptiveQuery without = copyWithoutGrouping(query);
            List<QueryFilter> rest = new ArrayList<>(filters);
            rest.remove(suspect);
            without.setFilters(rest);

            if (!execute(without, model).getRows().isEmpty()) {
                notes.add("Everything else matches; it is the condition on " + suspect.getField()
                        + " that leaves nothing. That combination simply does not occur in the data.");
                return;
            }
        }
    }

    /** A dimension that is blank on most rows usually applies to only part of the data. */
    private void notePartlyEmptyDimensions(AnalyticsResult result, DescriptiveQuery query) {
        if (result.getRowCount() == 0) {
            return;
        }
        for (QueryDimension dimension : query.getDimensions()) {
            long blank = result.getRows().stream()
                    .filter(row -> row.get(dimension.getField()) == null
                            || String.valueOf(row.get(dimension.getField())).isBlank())
                    .count();
            if (blank * 2 > result.getRowCount()) {
                result.getNotes().add("Most rows have no " + dimension.getField()
                        + ", because it is only recorded for part of the data. Narrow the question to "
                        + "the part that has it rather than reading this as missing data.");
            }
        }
    }

    private DescriptiveQuery copyWithoutGrouping(DescriptiveQuery query) {
        DescriptiveQuery copy = new DescriptiveQuery();
        copy.setDataset(query.getDataset());
        copy.setMetrics(query.getMetrics());
        copy.setFilters(query.getFilters());
        return copy;
    }

    /** Runs the same query over the period before, and adds the earlier value and the change. */
    private void addComparison(AnalyticsResult result, DescriptiveQuery query, SemanticModel model, DateRange period) {
        if (period == null) {
            throw new InvalidQueryException("Comparing with the previous period needs a date filter, "
                    + "for example a period like last_month.");
        }

        DescriptiveQuery previousQuery =
                PeriodResolver.shiftTo(query, model.dateDimension(), period.previous());
        List<Map<String, Object>> previousRows = execute(previousQuery, model).getRows();

        List<String> groupFields = dimensionFields(query);
        Map<String, Map<String, Object>> previousByGroup = new HashMap<>();
        for (Map<String, Object> row : previousRows) {
            previousByGroup.put(groupKey(row, groupFields), row);
        }

        for (Map<String, Object> row : result.getRows()) {
            Map<String, Object> previous = previousByGroup.get(groupKey(row, groupFields));
            for (String metric : query.getMetrics()) {
                Object earlier = previous == null ? null : previous.get(metric);
                row.put(metric + "_previous", earlier);
                row.put(metric + "_change_pct", changePercent(row.get(metric), earlier));
            }
        }

        for (String metric : query.getMetrics()) {
            result.getColumns().add(metric + "_previous");
            result.getColumns().add(metric + "_change_pct");
        }
    }

    private Double changePercent(Object current, Object earlier) {
        if (!(current instanceof Number now) || !(earlier instanceof Number before) || before.doubleValue() == 0) {
            return null;
        }
        double change = (now.doubleValue() - before.doubleValue()) / before.doubleValue() * 100;
        return Math.round(change * 100) / 100.0;
    }

    /** Warns when a column looks addable but is not, so nobody totals it by hand. */
    private void addNotes(AnalyticsResult result, DescriptiveQuery query, SemanticModel model) {
        if (query.getDimensions().isEmpty()) {
            return;
        }
        for (String metric : query.getMetrics()) {
            if (!model.isAdditive(metric)) {
                result.getNotes().add("The '" + metric + "' column counts distinct things per row, so adding the "
                        + "rows together would double count. Only compare rows, do not total them.");
            }
        }
    }

    private String groupKey(Map<String, Object> row, List<String> groupFields) {
        StringBuilder key = new StringBuilder();
        for (String field : groupFields) {
            key.append(String.valueOf(row.get(field))).append('\u0001');
        }
        return key.toString();
    }

    private List<String> dimensionFields(DescriptiveQuery query) {
        List<String> fields = new ArrayList<>();
        for (QueryDimension dimension : query.getDimensions()) {
            fields.add(dimension.getField());
        }
        return fields;
    }

    private SemanticModel findModel(String dataset) {
        String name = dataset == null || dataset.isBlank() ? defaultDataset : dataset;
        return registry.find(name).orElseThrow(() -> new InvalidQueryException(
                "Unknown dataset '" + name + "'. Available datasets: " + registry.datasets()));
    }

    private List<String> columnsOf(DescriptiveQuery query) {
        List<String> columns = new ArrayList<>(dimensionFields(query));
        for (String metric : query.getMetrics()) {
            columns.add(metric);
            if (query.isPercentOfTotal()) {
                columns.add(metric + "_pct");
            }
        }
        return columns;
    }
}
