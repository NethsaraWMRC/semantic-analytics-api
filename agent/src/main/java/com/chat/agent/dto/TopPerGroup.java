package com.chat.agent.dto;

import lombok.Data;

/**
 * "Top 3 products in each region" -> {"within": "region", "top": 3}.
 * The ranking uses the first sort entry, or the first metric if no sort was given.
 */
@Data
public class TopPerGroup {

    private String within;
    private Integer top;
}
