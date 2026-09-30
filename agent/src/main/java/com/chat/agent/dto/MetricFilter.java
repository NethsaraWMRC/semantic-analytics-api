package com.chat.agent.dto;

import lombok.Data;

/**
 * A condition on a computed total, e.g. {"metric": "revenue", "operator": ">", "value": 500}.
 * This becomes SQL's HAVING: it filters groups after adding them up, not rows before.
 */
@Data
public class MetricFilter {

    private String metric;
    private String operator;
    private Number value;
}
