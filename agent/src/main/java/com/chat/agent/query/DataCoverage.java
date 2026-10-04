package com.chat.agent.query;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chat.agent.repository.AnalyticsRepo;
import com.chat.agent.semantic.SemanticModel;

/**
 * The first and last dates a dataset actually holds.
 *
 * Without this the agent cannot tell "nothing was sold" from "nothing has been loaded yet", and
 * it will report an empty day as a business fact. Read once per dataset and kept, so a restart
 * is what picks up newly loaded data.
 */
@Component
public class DataCoverage {

    private static final Logger log = LoggerFactory.getLogger(DataCoverage.class);

    private final AnalyticsRepo repo;
    private final Map<String, DateRange> cache = new ConcurrentHashMap<>();

    public DataCoverage(AnalyticsRepo repo) {
        this.repo = repo;
    }

    /** null when the dataset has no dates at all, or the range could not be read. */
    public DateRange of(SemanticModel model) {
        String dateField = model.dateDimension();
        if (dateField == null) {
            return null;
        }
        return cache.computeIfAbsent(model.getDataset(), key -> load(model, dateField));
    }

    private DateRange load(SemanticModel model, String dateField) {
        String expression = model.getDimensions().get(dateField).getSql();

        StringBuilder sql = new StringBuilder("SELECT MIN(").append(expression).append("), MAX(")
                .append(expression).append(") FROM ").append(model.getBaseTable());
        for (String join : model.joinsFor(List.of(expression))) {
            sql.append(" ").append(join);
        }

        try {
            List<Map<String, Object>> rows = repo.query(sql.toString(), List.of());
            if (rows.isEmpty()) {
                return null;
            }

            List<Object> bounds = List.copyOf(rows.get(0).values());
            LocalDate first = parse(bounds.get(0));
            LocalDate last = parse(bounds.get(1));
            if (first == null || last == null) {
                return null;
            }

            log.info("{} covers {} to {}", model.getDataset(), first, last);
            return new DateRange(first, last.plusDays(1), ChronoUnit.DAYS,
                    ChronoUnit.DAYS.between(first, last.plusDays(1)));
        } catch (RuntimeException e) {
            log.warn("could not read the date range of {}: {}", model.getDataset(), e.getMessage());
            return null;
        }
    }

    private LocalDate parse(Object value) {
        if (value == null) {
            return null;
        }
        try {
            // dates arrive as text or as a timestamp, so take the leading yyyy-MM-dd either way
            String text = String.valueOf(value);
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
