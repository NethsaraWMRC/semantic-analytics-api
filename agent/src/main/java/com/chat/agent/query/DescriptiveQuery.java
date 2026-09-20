package com.chat.agent.query;

import java.util.List;

public record DescriptiveQuery(
        String dataset,
        List<String> metrics,
        List<Dimension> dimensions,
        List<Filter> filters,
        List<Sort> sort,
        Integer limit) {

    public record Dimension(String field, String grain) {}

    public record Filter(String field, String operator, Object value) {}

    public record Sort(String field, String direction) {}
}
