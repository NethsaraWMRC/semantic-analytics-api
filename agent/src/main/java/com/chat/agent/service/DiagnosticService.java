package com.chat.agent.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.chat.agent.dto.AnalyticsResult;
import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.dto.DiagnosticQuery;
import com.chat.agent.dto.DiagnosticResult;
import com.chat.agent.dto.Explanation;
import com.chat.agent.dto.Mover;
import com.chat.agent.dto.QueryDimension;
import com.chat.agent.dto.QueryFilter;
import com.chat.agent.query.DateRange;
import com.chat.agent.query.InvalidQueryException;
import com.chat.agent.query.PeriodResolver;
import com.chat.agent.semantic.SemanticField;
import com.chat.agent.semantic.SemanticModel;
import com.chat.agent.semantic.SemanticRegistry;

/**
 * Explains a change by decomposing it: the metric moved by X, and here are the segments that
 * account for X. Every figure comes from the descriptive engine, so validation, the semantic
 * layer, the row cap and the fan-out guard all still apply.
 *
 * It answers where the change happened, never why. The cause may well be something the
 * database has never heard of.
 */
@Service
public class DiagnosticService {

    private static final int MAX_EXPLAIN_BY = 4;
    private static final int DEFAULT_TOP = 5;

    /** below this share, no single segment is really responsible and we say so */
    private static final double WEAK_EXPLANATION = 25.0;

    private final AnalyticsService analytics;
    private final SemanticRegistry registry;
    private final String defaultDataset;

    public DiagnosticService(AnalyticsService analytics, SemanticRegistry registry,
                             @Value("${analytics.dataset}") String defaultDataset) {
        this.analytics = analytics;
        this.registry = registry;
        this.defaultDataset = defaultDataset;
    }

    public DiagnosticResult run(DiagnosticQuery query) {
        SemanticModel model = findModel(query.getDataset());
        validate(query, model);

        DateRange current = PeriodResolver.resolve(query.getPeriod(), LocalDate.now());
        DateRange previous = current.previous();

        DiagnosticResult result = new DiagnosticResult();
        result.setMetric(query.getMetric());
        result.setPeriod(describe(current));
        result.setComparedWith(describe(previous));

        double now = total(query, model, current);
        double before = total(query, model, previous);
        double change = round(now - before);

        result.setCurrent(round(now));
        result.setPrevious(round(before));
        result.setChange(change);
        result.setChangePercent(before == 0 ? null : round(change / Math.abs(before) * 100));

        for (String dimension : query.getExplainBy()) {
            result.getExplanations().add(
                    explainBy(query, model, dimension, current, previous, change, result.getNotes()));
        }

        addOverallNotes(result, change);
        return result;
    }

    /** The change broken down by one dimension, biggest mover first. */
    private Explanation explainBy(DiagnosticQuery query, SemanticModel model, String dimension,
                                  DateRange current, DateRange previous, double change, List<String> notes) {
        Map<String, Double> now = totalsByValue(query, model, dimension, current, notes);
        Map<String, Double> before = totalsByValue(query, model, dimension, previous, notes);

        // a segment that vanished is often the whole story, so walk both periods, not just this one
        Set<String> everyValue = new LinkedHashSet<>(now.keySet());
        everyValue.addAll(before.keySet());

        List<Mover> movers = new ArrayList<>();
        for (String value : everyValue) {
            double currentValue = now.getOrDefault(value, 0.0);
            double previousValue = before.getOrDefault(value, 0.0);
            double delta = currentValue - previousValue;

            if (delta != 0) {
                movers.add(new Mover(value, round(currentValue), round(previousValue), round(delta),
                        change == 0 ? null : round(delta / change * 100)));
            }
        }

        warnAboutNearDuplicates(dimension, everyValue, notes);
        movers.sort(Comparator.comparingDouble((Mover mover) -> Math.abs(mover.getChange())).reversed());

        int top = query.getTopContributors() == null ? DEFAULT_TOP : query.getTopContributors();
        Explanation explanation = new Explanation();
        explanation.setDimension(dimension);
        explanation.setMovers(new ArrayList<>(movers.subList(0, Math.min(top, movers.size()))));
        explanation.setPartial(movers.size() > top);
        return explanation;
    }

    /**
     * Values that differ only in spelling or case are the same thing wearing two labels.
     * Left unsaid, a rename between the two periods looks like a huge fall in one segment and a
     * huge rise in another, and whoever reads it chases a change that never happened.
     */
    private void warnAboutNearDuplicates(String dimension, Set<String> values, List<String> notes) {
        Map<String, List<String>> byShape = new LinkedHashMap<>();
        for (String value : values) {
            byShape.computeIfAbsent(shapeOf(value), key -> new ArrayList<>()).add(value);
        }

        for (List<String> spellings : byShape.values()) {
            if (spellings.size() > 1) {
                notes.add("The " + dimension + " values " + spellings + " differ only in spelling, so one "
                        + "thing is being counted as separate segments. Its real change is the sum of theirs, "
                        + "and a move between the two spellings is a relabelling, not a change in the business.");
            }
        }
    }

    private String shapeOf(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** One period's figure for every value of a dimension. */
    private Map<String, Double> totalsByValue(DiagnosticQuery query, SemanticModel model, String dimension,
                                              DateRange range, List<String> notes) {
        DescriptiveQuery descriptive = baseQuery(query, model, range);

        QueryDimension group = new QueryDimension();
        group.setField(dimension);
        descriptive.setDimensions(new ArrayList<>(List.of(group)));

        AnalyticsResult result = analytics.run(descriptive);
        if (result.isTruncated()) {
            notes.add("There were more " + dimension + " values than could be examined, so a smaller "
                    + "mover may have been missed.");
        }

        Map<String, Double> totals = new LinkedHashMap<>();
        for (Map<String, Object> row : result.getRows()) {
            Object value = row.get(dimension);
            totals.put(value == null ? "(none)" : String.valueOf(value), number(row.get(query.getMetric())));
        }
        return totals;
    }

    /** The metric for the whole period, with no breakdown. */
    private double total(DiagnosticQuery query, SemanticModel model, DateRange range) {
        AnalyticsResult result = analytics.run(baseQuery(query, model, range));
        return result.getRows().isEmpty() ? 0 : number(result.getRows().get(0).get(query.getMetric()));
    }

    /** The metric, the caller's own filters, and the date window, ready for the descriptive engine. */
    private DescriptiveQuery baseQuery(DiagnosticQuery query, SemanticModel model, DateRange range) {
        DescriptiveQuery descriptive = new DescriptiveQuery();
        descriptive.setDataset(model.getDataset());
        descriptive.setMetrics(new ArrayList<>(List.of(query.getMetric())));

        List<QueryFilter> filters = new ArrayList<>(query.getFilters());
        filters.add(bound(model.dateDimension(), ">=", range.getStart().toString()));
        filters.add(bound(model.dateDimension(), "<", range.getEndExclusive().toString()));
        descriptive.setFilters(filters);

        return descriptive;
    }

    private QueryFilter bound(String field, String operator, String value) {
        QueryFilter filter = new QueryFilter();
        filter.setField(field);
        filter.setOperator(operator);
        filter.setValue(value);
        return filter;
    }

    private void addOverallNotes(DiagnosticResult result, double change) {
        if (change == 0) {
            result.getNotes().add("The figure did not move between the two periods.");
            return;
        }

        boolean anythingStandsOut = result.getExplanations().stream()
                .flatMap(explanation -> explanation.getMovers().stream())
                .anyMatch(mover -> mover.getShareOfChange() != null
                        && Math.abs(mover.getShareOfChange()) >= WEAK_EXPLANATION);

        if (!anythingStandsOut) {
            result.getNotes().add("The change is spread thinly across many segments, so no single one "
                    + "explains it.");
        }

        boolean offsetting = result.getExplanations().stream()
                .flatMap(explanation -> explanation.getMovers().stream())
                .anyMatch(mover -> mover.getShareOfChange() != null && Math.abs(mover.getShareOfChange()) > 100);

        if (offsetting) {
            result.getNotes().add("Some segments rose while others fell, so a single share can exceed "
                    + "100 percent of the net change.");
        }
    }

    private void validate(DiagnosticQuery query, SemanticModel model) {
        if (query.getMetric() == null || !model.getMetrics().containsKey(query.getMetric())) {
            throw new InvalidQueryException("Unknown metric " + quote(query.getMetric())
                    + ". Available metrics: " + model.getMetrics().keySet());
        }
        if (model.dateDimension() == null) {
            throw new InvalidQueryException("The " + model.getDataset()
                    + " dataset has no dates, so a change over time cannot be explained.");
        }
        if (query.getExplainBy().isEmpty()) {
            throw new InvalidQueryException("Name at least one dimension to break the change down by. "
                    + "Available: " + explainable(model));
        }
        if (query.getExplainBy().size() > MAX_EXPLAIN_BY) {
            throw new InvalidQueryException("At most " + MAX_EXPLAIN_BY
                    + " dimensions can be explained at once.");
        }
        for (String dimension : query.getExplainBy()) {
            SemanticField field = model.getDimensions().get(dimension);
            if (field == null || !field.isExplain()) {
                throw new InvalidQueryException("A change cannot be broken down by " + quote(dimension)
                        + ". Available: " + explainable(model));
            }
        }
        if (query.getTopContributors() != null
                && (query.getTopContributors() < 1 || query.getTopContributors() > 20)) {
            throw new InvalidQueryException("topContributors must be between 1 and 20.");
        }
    }

    private List<String> explainable(SemanticModel model) {
        return model.getDimensions().entrySet().stream()
                .filter(entry -> entry.getValue().isExplain())
                .map(Map.Entry::getKey)
                .toList();
    }

    private SemanticModel findModel(String dataset) {
        String name = dataset == null || dataset.isBlank() ? defaultDataset : dataset;
        return registry.find(name).orElseThrow(() -> new InvalidQueryException(
                "Unknown dataset " + quote(name) + ". Available datasets: " + registry.datasets()));
    }

    private String quote(String value) {
        return "'" + value + "'";
    }

    private String describe(DateRange range) {
        return range.getStart() + " to " + range.getEndExclusive().minusDays(1);
    }

    private double number(Object value) {
        return value instanceof Number figure ? figure.doubleValue() : 0;
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
