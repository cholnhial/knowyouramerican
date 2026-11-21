package com.knowyouramerican.puzzle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PuzzleCalendarTest {

    private static PuzzleCalendar at(String instant) {
        return new PuzzleCalendar(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void latestPlayableIsTodayInUtcPlus14() {
        // 09:59 UTC on Nov 21 is 23:59 Nov 21 in Kiribati (UTC+14)...
        assertThat(at("2026-11-21T09:59:00Z").latestPlayable()).isEqualTo(LocalDate.of(2026, 11, 21));
        // ...and at 10:00 UTC Kiribati is already on Nov 22.
        assertThat(at("2026-11-21T10:00:00Z").latestPlayable()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void playableRangeStartsOnFirstDayAndStopsAtLatest() {
        PuzzleCalendar cal = at("2026-11-21T12:00:00Z");
        assertThat(cal.isPlayable(LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(cal.isPlayable(PuzzleCalendar.FIRST_DAY)).isTrue();
        assertThat(cal.isPlayable(LocalDate.of(2026, 11, 22))).isTrue();
        assertThat(cal.isPlayable(LocalDate.of(2026, 11, 23))).isFalse();
    }
}
