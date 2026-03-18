package sotd.song;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Periods supported by the pairwise shared-song endpoint.
 *
 * <p>The current implementation reads from daily rollups and aggregates them into larger windows at
 * query time so week/month support does not require a separate ingestion path yet.
 */
public enum OurSongPeriodType {
    DAY {
        @Override
        public PeriodWindow resolveWindow(LocalDate anchorDate) {
            return new PeriodWindow(this, anchorDate, anchorDate.plusDays(1));
        }
    },
    WEEK {
        @Override
        public PeriodWindow resolveWindow(LocalDate anchorDate) {
            LocalDate periodStartLocal = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new PeriodWindow(this, periodStartLocal, periodStartLocal.plusWeeks(1));
        }
    },
    MONTH {
        @Override
        public PeriodWindow resolveWindow(LocalDate anchorDate) {
            LocalDate periodStartLocal = anchorDate.withDayOfMonth(1);
            return new PeriodWindow(this, periodStartLocal, periodStartLocal.plusMonths(1));
        }
    };

    public abstract PeriodWindow resolveWindow(LocalDate anchorDate);

    public record PeriodWindow(
            OurSongPeriodType periodType,
            LocalDate periodStartLocal,
            LocalDate periodEndExclusive
    ) {
    }
}
