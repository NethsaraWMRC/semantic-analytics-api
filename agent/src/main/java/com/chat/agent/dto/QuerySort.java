package com.chat.agent.dto;

import lombok.Data;

/** One ordering, e.g. {"field": "revenue", "direction": "DESC"}. */
@Data
public class QuerySort {

    private String field;
    private String direction;
}
