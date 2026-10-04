package com.chat.agent.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * "Why did revenue fall last month?" — one metric, two periods, and the dimensions to
 * break the change down by. The answer is always which segments moved, never why they did.
 */
@Data
public class DiagnosticQuery {

    /** always "diagnostic"; how the agent tells this apart from a descriptive query */
    private String type;

    private String dataset;

    /** the single measure being explained */
    private String metric;

    /** a period name such as last_month; the comparison is the period immediately before it */
    private String period;

    /** dimensions to decompose the change by, best first */
    private List<String> explainBy = new ArrayList<>();

    /** optional narrowing, e.g. only Badminton Shoes */
    private List<QueryFilter> filters = new ArrayList<>();

    /** how many movers to report per dimension */
    private Integer topContributors;

    public void setExplainBy(List<String> explainBy) {
        this.explainBy = explainBy == null ? new ArrayList<>() : explainBy;
    }

    public void setFilters(List<QueryFilter> filters) {
        this.filters = filters == null ? new ArrayList<>() : filters;
    }
}
