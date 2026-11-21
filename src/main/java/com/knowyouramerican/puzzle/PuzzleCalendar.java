package com.knowyouramerican.puzzle;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Which dates are playable. "Today" is the player's local date, so the newest playable date is
 * today in the furthest-ahead timezone (UTC+14). Anything later is a peek into the future.
 */
@Component
public class PuzzleCalendar {

    public static final LocalDate FIRST_DAY = LocalDate.of(2026, 1, 1);
    private static final Duration MAX_AHEAD_OF_UTC = Duration.ofHours(14);

    private final Clock clock;

    public PuzzleCalendar(Clock clock) {
        this.clock = clock;
    }

    /** Newest date anyone on Earth can currently be on. */
    public LocalDate latestPlayable() {
        return LocalDate.ofInstant(clock.instant().plus(MAX_AHEAD_OF_UTC), ZoneOffset.UTC);
    }

    /** Server's idea of today, used only when the browser can't tell us (no JS). */
    public LocalDate utcToday() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public boolean isPlayable(LocalDate date) {
        return !date.isBefore(FIRST_DAY) && !date.isAfter(latestPlayable());
    }
}
