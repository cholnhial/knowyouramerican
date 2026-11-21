package com.knowyouramerican.puzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowyouramerican.support.TempDatabase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** With the scheduler on, startup backfills from the first day through UTC today + 2. */
@SpringBootTest
class PuzzleSchedulerTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        TempDatabase.register(registry);
        registry.add("kya.puzzles.scheduler-enabled", () -> "true");
    }

    @Autowired PuzzleRepository puzzles;
    @Autowired PuzzleCalendar calendar;
    @Autowired PuzzleScheduler scheduler;

    @Test
    void startupBackfillCoversEveryPlayableDay() {
        LocalDate last = calendar.utcToday().plusDays(PuzzleScheduler.DAYS_AHEAD);
        long expected = PuzzleCalendar.FIRST_DAY.datesUntil(last.plusDays(1)).count();

        assertThat(puzzles.count()).isEqualTo(expected);
        assertThat(puzzles.exists(calendar.latestPlayable())).isTrue();

        scheduler.hourly(); // a later run finds nothing to do
        assertThat(puzzles.count()).isEqualTo(expected);
    }
}
