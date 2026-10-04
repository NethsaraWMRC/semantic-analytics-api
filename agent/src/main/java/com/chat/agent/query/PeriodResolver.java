package com.chat.agent.query;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.chat.agent.dto.DescriptiveQuery;
import com.chat.agent.dto.QueryFilter;
import com.chat.agent.semantic.SemanticModel;

/**
 * Turns a named period such as "last_month" into real dates. The LLM only picks a name from
 * this list; all the calendar arithmetic happens here, where it can be tested. Weeks start
 * on Monday and every window includes its start date and excludes its end date.
 */
public final class PeriodResolver {

    public static final Set<String> PERIODS = new LinkedHashSet<>(List.of(
            "today", "yesterday", "last_7_days", "last_30_days", "last_90_days",
            "this_week", "last_week", "this_month", "last_month",
            "this_quarter", "last_quarter", "this_year", "last_year",
            "last_monday", "last_tuesday", "last_wednesday", "last_thursday",
            "last_friday", "last_saturday", "last_sunday"));

    private PeriodResolver() {
    }

    public static DateRange resolve(String period, LocalDate today) {
        return switch (period == null ? "" : period) {
            case "today" -> days(today, 1);
            case "yesterday" -> days(today.minusDays(1), 1);
            case "last_7_days" -> days(today.minusDays(6), 7);
            case "last_30_days" -> days(today.minusDays(29), 30);
            case "last_90_days" -> days(today.minusDays(89), 90);
            case "this_week" -> weeks(today.with(DayOfWeek.MONDAY), 1);
            case "last_week" -> weeks(today.with(DayOfWeek.MONDAY).minusWeeks(1), 1);
            case "this_month" -> months(today.withDayOfMonth(1), 1);
            case "last_month" -> months(today.withDayOfMonth(1).minusMonths(1), 1);
            case "this_quarter" -> months(firstDayOfQuarter(today), 3);
            case "last_quarter" -> months(firstDayOfQuarter(today).minusMonths(3), 3);
            case "this_year" -> years(today.withDayOfYear(1), 1);
            case "last_year" -> years(today.withDayOfYear(1).minusYears(1), 1);
            case "last_monday" -> lastWeekday(today, DayOfWeek.MONDAY);
            case "last_tuesday" -> lastWeekday(today, DayOfWeek.TUESDAY);
            case "last_wednesday" -> lastWeekday(today, DayOfWeek.WEDNESDAY);
            case "last_thursday" -> lastWeekday(today, DayOfWeek.THURSDAY);
            case "last_friday" -> lastWeekday(today, DayOfWeek.FRIDAY);
            case "last_saturday" -> lastWeekday(today, DayOfWeek.SATURDAY);
            case "last_sunday" -> lastWeekday(today, DayOfWeek.SUNDAY);
            default -> throw new InvalidQueryException(
                    "Unknown period '" + period + "'. Available periods: " + PERIODS);
        };
    }

    /**
     * Replaces every {"period": "..."} filter with real >= and < bounds, and reports the date
     * window the query ended up covering (null when it has no date filter at all).
     */
    public static DateRange expand(DescriptiveQuery query, SemanticModel model, LocalDate today) {
        String dateField = model.dateDimension();
        List<QueryFilter> expanded = new ArrayList<>();
        DateRange named = null;

        for (QueryFilter filter : query.getFilters()) {
            if (filter == null || filter.getPeriod() == null) {
                expanded.add(filter);
                continue;
            }
            if (dateField == null) {
                throw new InvalidQueryException("This dataset has no date field, so time periods cannot be used.");
            }
            if (!dateField.equals(filter.getField())) {
                throw new InvalidQueryException("A period can only be used on the date field '" + dateField
                        + "', not on '" + filter.getField() + "'.");
            }
            named = resolve(filter.getPeriod(), today);
            expanded.add(bound(dateField, ">=", named.getStart()));
            expanded.add(bound(dateField, "<", named.getEndExclusive()));
        }
        query.setFilters(expanded);

        return named != null ? named : rangeFromBounds(query, dateField);
    }

    /** Swaps the date bounds for a different window, keeping every other filter. */
    public static DescriptiveQuery shiftTo(DescriptiveQuery query, String dateField, DateRange range) {
        DescriptiveQuery copy = new DescriptiveQuery();
        copy.setDataset(query.getDataset());
        copy.setMetrics(query.getMetrics());
        copy.setDimensions(query.getDimensions());
        copy.setSort(query.getSort());
        copy.setHaving(query.getHaving());
        copy.setLimit(query.getLimit());
        copy.setTopPerGroup(query.getTopPerGroup());

        List<QueryFilter> filters = new ArrayList<>();
        for (QueryFilter filter : query.getFilters()) {
            if (!isDateBound(filter, dateField)) {
                filters.add(filter);
            }
        }
        filters.add(bound(dateField, ">=", range.getStart()));
        filters.add(bound(dateField, "<", range.getEndExclusive()));
        copy.setFilters(filters);
        return copy;
    }

    /** Reads the window back out of plain >= and < filters, so comparisons work without a period name. */
    private static DateRange rangeFromBounds(DescriptiveQuery query, String dateField) {
        LocalDate start = null;
        LocalDate end = null;
        for (QueryFilter filter : query.getFilters()) {
            if (!isDateBound(filter, dateField)) {
                continue;
            }
            LocalDate value = parseOrNull(filter.getValue());
            if (value == null) {
                continue;
            }
            if (">=".equals(filter.getOperator()) || ">".equals(filter.getOperator())) {
                start = value;
            } else {
                end = value;
            }
        }
        if (start == null || end == null) {
            return null;
        }
        return new DateRange(start, end, ChronoUnit.DAYS, ChronoUnit.DAYS.between(start, end));
    }

    private static boolean isDateBound(QueryFilter filter, String dateField) {
        return filter != null && dateField != null && dateField.equals(filter.getField())
                && Set.of(">=", ">", "<", "<=").contains(String.valueOf(filter.getOperator()));
    }

    private static LocalDate parseOrNull(Object value) {
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static QueryFilter bound(String field, String operator, LocalDate value) {
        QueryFilter filter = new QueryFilter();
        filter.setField(field);
        filter.setOperator(operator);
        filter.setValue(value.toString());
        return filter;
    }

    /** The most recent day with that name, never today itself. One day, not the week around it. */
    private static DateRange lastWeekday(LocalDate today, DayOfWeek weekday) {
        LocalDate day = today.minusDays(1);
        while (day.getDayOfWeek() != weekday) {
            day = day.minusDays(1);
        }
        return days(day, 1);
    }

    private static LocalDate firstDayOfQuarter(LocalDate date) {
        int firstMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(date.getYear(), firstMonth, 1);
    }

    private static DateRange days(LocalDate start, long count) {
        return new DateRange(start, start.plusDays(count), ChronoUnit.DAYS, count);
    }

    private static DateRange weeks(LocalDate start, long count) {
        return new DateRange(start, start.plusWeeks(count), ChronoUnit.WEEKS, count);
    }

    private static DateRange months(LocalDate start, long count) {
        return new DateRange(start, start.plusMonths(count), ChronoUnit.MONTHS, count);
    }

    private static DateRange years(LocalDate start, long count) {
        return new DateRange(start, start.plusYears(count), ChronoUnit.YEARS, count);
    }
}
