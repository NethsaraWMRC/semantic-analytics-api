package com.chat.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.chat.agent.query.DescriptiveQuery;
import com.chat.agent.query.QueryValidator;
import com.chat.agent.query.SqlBuilder;
import com.chat.agent.query.SqlBuilder.BuiltSql;
import com.chat.agent.repository.AnalyticsRepo;
import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class AnalyticsService {

    public record Result(
            List<String> columns,
            List<Map<String, Object>> rows,
            @JsonProperty("row_count") int rowCount,
            boolean truncated) {}

    private final QueryValidator validator;
    private final SqlBuilder builder;
    private final AnalyticsRepo repo;

    public AnalyticsService(QueryValidator validator, SqlBuilder builder, AnalyticsRepo repo) {
        this.validator = validator;
        this.builder = builder;
        this.repo = repo;
    }

    public Result run(DescriptiveQuery q) {
        validator.validate(q);

        int max = QueryValidator.MAX_LIMIT;
        int effective = q.limit() == null ? max : Math.min(q.limit(), max);

        // fetch one extra row to know whether more rows existed
        BuiltSql built = builder.build(q, effective + 1);
        List<Map<String, Object>> rows = repo.query(built.sql(), built.params());

        boolean hasMore = rows.size() > effective;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, effective));
        }
        boolean truncated = hasMore && (q.limit() == null || q.limit() > max);

        List<String> columns = new ArrayList<>();
        if (q.dimensions() != null) {
            q.dimensions().forEach(d -> columns.add(d.field()));
        }
        columns.addAll(q.metrics());

        return new Result(columns, rows, rows.size(), truncated);
    }
}
