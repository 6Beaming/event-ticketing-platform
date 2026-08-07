package common;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A calendar-date range represented as a half-open timestamp interval.
 */
public final class DateRange {
    private final LocalDateTime startInclusive;
    private final LocalDateTime endExclusive;

    private DateRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
    }

    public static DateRange fromInclusiveDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
        return new DateRange(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );
    }

    public LocalDateTime getStartInclusive() {
        return startInclusive;
    }

    public LocalDateTime getEndExclusive() {
        return endExclusive;
    }
}
