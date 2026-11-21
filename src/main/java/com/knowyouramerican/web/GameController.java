package com.knowyouramerican.web;

import com.knowyouramerican.background.BackgroundCatalog;
import com.knowyouramerican.puzzle.PuzzleCalendar;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The daily game. {@code /} is a shell that asks the browser for its local date and then loads
 * {@code /play/{date}} via HTMX. Visiting {@code /play/{date}} directly renders the full page.
 *
 * <p>Round 1: questions are a static demo card; real puzzles arrive with the generator.
 */
@Controller
public class GameController {

    static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);

    private final BackgroundCatalog backgrounds;
    private final PuzzleCalendar calendar;

    public GameController(BackgroundCatalog backgrounds, PuzzleCalendar calendar) {
        this.backgrounds = backgrounds;
        this.calendar = calendar;
    }

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("background", backgrounds.random());
        model.addAttribute("fallbackDate", calendar.utcToday());
        model.addAttribute("preloaded", false);
        return "index";
    }

    @GetMapping("/play/{date}")
    String play(@PathVariable String date,
                @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                Model model) {
        LocalDate day = parsePlayable(date);
        model.addAttribute("day", day);
        model.addAttribute("dayLabel", DAY_LABEL.format(day));
        model.addAttribute("demo", DemoQuestion.SAMPLE);
        model.addAttribute("results", List.of("pending", "pending", "pending", "pending", "pending"));

        if (hxRequest != null) {
            return "play :: game";
        }
        model.addAttribute("background", backgrounds.random());
        model.addAttribute("fallbackDate", day);
        model.addAttribute("preloaded", true);
        return "index";
    }

    /** Round-1 demo of the answer flow: feedback fragment + out-of-band progress update. */
    @PostMapping("/play/demo/answer")
    String demoAnswer(@RequestParam int choice, Model model) {
        DemoQuestion q = DemoQuestion.SAMPLE;
        if (choice < 0 || choice >= q.options().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "choice out of range");
        }
        model.addAttribute("demo", q);
        model.addAttribute("choice", choice);
        boolean correct = choice == q.correctIndex();
        model.addAttribute("correct", correct);
        model.addAttribute("results",
                List.of(correct ? "correct" : "missed", "pending", "pending", "pending", "pending"));
        return "play :: demo-feedback";
    }

    private LocalDate parsePlayable(String raw) {
        LocalDate day;
        try {
            day = LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such day");
        }
        if (!calendar.isPlayable(day)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That day isn't playable");
        }
        return day;
    }

    /** Placeholder question used until real puzzles are generated (round 3). */
    public record DemoQuestion(String typeLabel, String idiom, String prompt,
                               List<String> options, int correctIndex, String example) {
        static final DemoQuestion SAMPLE = new DemoQuestion(
                "What does it mean?",
                "bite the bullet",
                "Pick the meaning that fits.",
                List.of(
                        "to eat something very quickly",
                        "to speak angrily to someone",
                        "to accept something difficult and face it bravely",
                        "to quit before finishing a task"),
                2,
                "I didn't want to go to the dentist, but I bit the bullet and made an appointment.");
    }
}
