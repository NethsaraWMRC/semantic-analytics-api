package com.chat.agent.dto;

import lombok.Data;

/** One grouping, e.g. {"field": "order_date", "grain": "month"}. Grain is optional. */
@Data
public class QueryDimension {

    private String field;
    private String grain;
}
