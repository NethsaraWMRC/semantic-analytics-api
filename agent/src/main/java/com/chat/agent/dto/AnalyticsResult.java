package com.chat.agent.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What POST /analytics/descriptive sends back, and what the LLM reads to write its answer. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResult {

    private List<String> columns;
    private List<Map<String, Object>> rows;

    @JsonProperty("row_count")
    private int rowCount;

    /** true when the result hit the server-side row cap, so it is not the full answer. */
    private boolean truncated;

    /** caveats the answer must mention, e.g. that a column cannot be added up. */
    private List<String> notes = new ArrayList<>();
}
