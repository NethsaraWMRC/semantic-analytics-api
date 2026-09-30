package com.chat.agent.dto;

import lombok.Data;

/**
 * One condition. Either a plain comparison:
 *   {"field": "region", "operator": "=", "value": "Western"}
 * or, on a date field, a named period the backend resolves itself:
 *   {"field": "order_date", "period": "last_month"}
 */
@Data
public class QueryFilter {

    private String field;
    private String operator;

    /** a string, a number, or a list for the "in" and "between" operators. */
    private Object value;

    /** e.g. last_month, this_year, last_7_days. Replaces operator and value. */
    private String period;
}
