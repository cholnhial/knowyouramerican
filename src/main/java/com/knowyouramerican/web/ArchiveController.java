package com.knowyouramerican.web;

import com.knowyouramerican.background.BackgroundCatalog;
import com.knowyouramerican.puzzle.PuzzleCalendar;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Past days: a month calendar ({@code /archive?year=&month=}) and a random day.
 * Changing the month swaps just the calendar over HTMX and updates the URL.
 */
@Controller
public class ArchiveController {

    private final BackgroundCatalog backgrounds;
    private final PuzzleCalendar calendar;

    public ArchiveController(BackgroundCatalog backgrounds, PuzzleCalendar calendar) {
        this.backgrounds = backgrounds;
        this.calendar = calendar;
    }

    @GetMapping("/archive")
    String archive(@RequestParam(required = false) Integer year,
                   @RequestParam(required = false) Integer month,
                   @RequestHeader(value = "HX-Request", required = false) String hx,
                   @RequestHeader(value = "HX-Target", required = false) String hxTarget,
                   Model model) {
        LocalDate last = lastDay();
        YearMonth wanted = YearMonth.from(last);
        if (year != null && month != null && month >= 1 && month <= 12) {
            wanted = YearMonth.of(year, month);
        } else if (year != null) {
            wanted = YearMonth.of(year, year == last.getYear() ? last.getMonthValue() : 1);
        }
        // Clamp into the archive's range.
        YearMonth firstMonth = YearMonth.from(PuzzleCalendar.FIRST_DAY);
        if (wanted.isBefore(firstMonth)) wanted = firstMonth;
        if (wanted.isAfter(YearMonth.from(last))) wanted = YearMonth.from(last);

        model.addAttribute("m", MonthView.of(wanted, PuzzleCalendar.FIRST_DAY, last, calendar.utcToday()));
        if (hx != null && "calendar".equals(hxTarget)) {
            return "fragments/calendar :: calendar";
        }
        model.addAttribute("background", backgrounds.random());
        model.addAttribute("active", "archive");
        return "archive";
    }

    @GetMapping("/archive/random")
    String random() {
        LocalDate last = lastDay();
        long span = ChronoUnit.DAYS.between(PuzzleCalendar.FIRST_DAY, last);
        LocalDate day = PuzzleCalendar.FIRST_DAY.plusDays(ThreadLocalRandom.current().nextLong(span + 1));
        return "redirect:/play/" + day;
    }

    /** Newest archive day: UTC today (players ahead of UTC get tomorrow from the home page). */
    private LocalDate lastDay() {
        return calendar.utcToday();
    }
}
