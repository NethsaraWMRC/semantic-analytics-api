package com.chat.agent.semantic;

import lombok.Data;

/** One metric or dimension, as written in the dataset's JSON file. */
@Data
public class SemanticField {

    /**
     * For a metric: how to aggregate — sum, count, count_distinct, min, max, average or ratio.
     * For a dimension: "date" marks it as a date, which enables period filters and date checking.
     */
    private String type;

    /** the column or expression to aggregate (metrics) or group by (dimensions). */
    private String sql;

    /** for type "ratio" only: the names of the two metrics to divide. */
    private String numerator;
    private String denominator;

    private String description;

    /** true when loose user wording should be matched against the real values of this dimension. */
    private boolean resolve;
}
