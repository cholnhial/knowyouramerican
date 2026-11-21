package com.knowyouramerican.puzzle;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * "The cron": at startup and then hourly, make sure puzzles exist from the first day through
 * two days past today in UTC. That covers every timezone's "today" (UTC−12 … UTC+14) with a
 * day to spare, so a missed run never leaves players without a puzzle.
 *
 * <p>Disable with {@code kya.puzzles.scheduler-enabled=false} (tests do).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "kya.puzzles.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class PuzzleScheduler {

    static final int DAYS_AHEAD = 2;

    private final PuzzleService service;
    private final PuzzleCalendar calendar;

    public PuzzleScheduler(PuzzleService service, PuzzleCalendar calendar) {
        this.service = service;
        this.calendar = calendar;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void hourly() {
        run();
    }

    void run() {
        service.ensureThrough(calendar.utcToday().plusDays(DAYS_AHEAD));
    }
}
