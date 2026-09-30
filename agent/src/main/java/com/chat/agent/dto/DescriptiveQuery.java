package com.chat.agent.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * The structured query the LLM produces instead of SQL, and the body of
 * POST /analytics/descriptive. The list setters turn null into an empty list,
 * so callers never need a null check.
 */
@Data
public class DescriptiveQuery {

    private String dataset;
    private List<String> metrics = new ArrayList<>();
    private List<QueryDimension> dimensions = new ArrayList<>();
    private List<QueryFilter> filters = new ArrayList<>();
    private List<QuerySort> sort = new ArrayList<>();
    private Integer limit;

    /** conditions on the totals, applied after grouping. */
    private List<MetricFilter> having = new ArrayList<>();

    /** adds a <metric>_pct column showing each row's share of the total. */
    private boolean percentOfTotal;

    /** keep only the best N rows inside each group. */
    private TopPerGroup topPerGroup;

    /** "previous_period" adds <metric>_previous and <metric>_change_pct columns. */
    private String compareTo;

    public void setMetrics(List<String> metrics) {
        this.metrics = metrics == null ? new ArrayList<>() : metrics;
    }

    public void setDimensions(List<QueryDimension> dimensions) {
        this.dimensions = dimensions == null ? new ArrayList<>() : dimensions;
    }

    public void setFilters(List<QueryFilter> filters) {
        this.filters = filters == null ? new ArrayList<>() : filters;
    }

    public void setSort(List<QuerySort> sort) {
        this.sort = sort == null ? new ArrayList<>() : sort;
    }

    public void setHaving(List<MetricFilter> having) {
        this.having = having == null ? new ArrayList<>() : having;
    }
}
