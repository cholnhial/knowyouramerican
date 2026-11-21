package com.knowyouramerican.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One archive month as a Sunday-first calendar grid.
 *
 * @param weeks rows of 7 cells; cells outside the month are {@link Day#blank()}
 */
public record MonthView(
        YearMonth month,
        String label,
        List<List<Day>> weeks,
        YearMonth previous,
        YearMonth next,
        List<Integer> years,
        List<Option> months,
        int playableCount) {

    /** A calendar cell. {@code date == null} means padding outside the month. */
    public record Day(LocalDate date, int dayOfMonth, boolean playable, boolean today) {
        static Day blank() {
            return new Day(null, 0, false, false);
        }
    }

    /** A month in the picker. */
    public record Option(int value, String label, boolean enabled) {}

    public static final List<String> WEEKDAYS = List.of("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");

    public List<String> weekdays() {
        return WEEKDAYS;
    }

    public int year() {
        return month.getYear();
    }

    public int monthValue() {
        return month.getMonthValue();
    }

    static MonthView of(YearMonth month, LocalDate first, LocalDate last, LocalDate today) {
        List<List<Day>> weeks = new ArrayList<>();
        List<Day> week = new ArrayList<>();
        int lead = month.atDay(1).getDayOfWeek().getValue() % 7; // Sunday = 0
        for (int i = 0; i < lead; i++) week.add(Day.blank());
        int playable = 0;
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            boolean ok = !date.isBefore(first) && !date.isAfter(last);
            if (ok) playable++;
            week.add(new Day(date, d, ok, date.equals(today)));
            if (week.size() == 7) {
                weeks.add(week);
                week = new ArrayList<>();
            }
        }
        if (!week.isEmpty()) {
            while (week.size() < 7) week.add(Day.blank());
            weeks.add(week);
        }

        YearMonth firstMonth = YearMonth.from(first);
        YearMonth lastMonth = YearMonth.from(last);
        List<Integer> years = new ArrayList<>();
        for (int y = firstMonth.getYear(); y <= lastMonth.getYear(); y++) years.add(y);
        List<Option> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(month.getYear(), m);
            months.add(new Option(m, ym.getMonth().getDisplayName(TextStyle.FULL, Locale.US),
                    !ym.isBefore(firstMonth) && !ym.isAfter(lastMonth)));
        }
        String label = month.getMonth().getDisplayName(TextStyle.FULL, Locale.US) + " " + month.getYear();
        return new MonthView(month, label, weeks,
                month.minusMonths(1).isBefore(firstMonth) ? null : month.minusMonths(1),
                month.plusMonths(1).isAfter(lastMonth) ? null : month.plusMonths(1),
                years, months, playable);
    }
}
