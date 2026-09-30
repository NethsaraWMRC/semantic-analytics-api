package com.chat.agent.query;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A date window: start is included, end is not. */
@Getter
@AllArgsConstructor
public class DateRange {

    private final LocalDate start;
    private final LocalDate endExclusive;

    /** the unit and length the window was built from, so "the period before" is exact. */
    private final ChronoUnit unit;
    private final long length;

    /** the same-sized window immediately before this one. */
    public DateRange previous() {
        return new DateRange(start.minus(length, unit), endExclusive.minus(length, unit), unit, length);
    }
}
