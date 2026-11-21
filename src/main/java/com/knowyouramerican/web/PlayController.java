package com.knowyouramerican.web;

import com.knowyouramerican.background.BackgroundCatalog;
import com.knowyouramerican.puzzle.PuzzleCalendar;
import com.knowyouramerican.puzzle.Question;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The daily game.
 *
 * <ul>
 *   <li>{@code GET /} and {@code GET /play/{date}} (normal navigation) render the page shell.
 *       {@code #game} then loads its content over HTMX, sending the browser's local date
 *       ({@code today}) and saved answers ({@code a}) from localStorage.</li>
 *   <li>{@code GET /play/{date|today}} with {@code HX-Request}: the game fragment.
 *       {@code q} picks a card (1..5); default is the first unanswered card, else the summary.</li>
 *   <li>{@code POST /play/{date}/q/{pos}}: answer a card. Responds with the re-rendered game and
 *       an {@code HX-Trigger: kya:answered} event that game.js stores in localStorage.</li>
 * </ul>
 */
@Controller
public class PlayController {

    private final GameViews games;
    private final BackgroundCatalog backgrounds;
    private final PuzzleCalendar calendar;

    public PlayController(GameViews games, BackgroundCatalog backgrounds, PuzzleCalendar calendar) {
        this.games = games;
        this.backgrounds = backgrounds;
        this.calendar = calendar;
    }

    @GetMapping("/")
    String home(Model model) {
        return shell(model, null);
    }

    @GetMapping("/play/{date}")
    String play(@PathVariable String date,
                @RequestParam(name = "a", required = false) String a,
                @RequestParam(name = "q", defaultValue = "0") int q,
                @RequestParam(name = "today", required = false) String today,
                @RequestHeader(value = "HX-Request", required = false) String hx,
                Model model) {
        LocalDate playerToday = parseOrNull(today);
        LocalDate day = resolve(date, playerToday);
        if (hx == null) {
            return shell(model, "today".equals(date) ? null : day);
        }
        List<Question> questions = games.questions(day).orElseThrow(PlayController::notFound);
        model.addAttribute("g", games.build(day, questions, Answers.parse(a), q, 0, playerToday, siteUrl()));
        return "play :: game";
    }

    @PostMapping("/play/{date}/q/{pos}")
    String answer(@PathVariable String date,
                  @PathVariable int pos,
                  @RequestParam int choice,
                  @RequestParam(name = "a", required = false) String a,
                  @RequestParam(name = "today", required = false) String today,
                  HttpServletResponse response,
                  Model model) {
        LocalDate playerToday = parseOrNull(today);
        LocalDate day = resolve(date, playerToday);
        if (pos < 1 || pos > Answers.SIZE || choice < 0 || choice > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad position or choice");
        }
        List<Question> questions = games.questions(day).orElseThrow(PlayController::notFound);
        Answers answers = Answers.parse(a);
        int just = 0;
        if (!answers.answered(pos)) { // one shot: a second answer for the same card is ignored
            answers = answers.with(pos, choice);
            just = pos;
            boolean correct = questions.get(pos - 1).correctIndex() == choice;
            response.setHeader("HX-Trigger", """
                    {"kya:answered":{"date":"%s","pos":%d,"choice":%d,"correct":%b}}"""
                    .formatted(day, pos, choice, correct));
        }
        model.addAttribute("g", games.build(day, questions, answers, pos, just, playerToday, siteUrl()));
        return "play :: game";
    }

    private String shell(Model model, LocalDate day) {
        model.addAttribute("background", backgrounds.random());
        model.addAttribute("gameDate", day);
        model.addAttribute("gameUrl", day == null ? "/play/today" : "/play/" + day);
        model.addAttribute("active", day == null ? "today" : "archive");
        return "index";
    }

    /** "today" → the player's local date when sane, else UTC today. Other dates must be playable. */
    private LocalDate resolve(String raw, LocalDate playerToday) {
        if ("today".equals(raw)) {
            return playerToday != null && calendar.isPlayable(playerToday) ? playerToday : calendar.utcToday();
        }
        LocalDate day = parseOrNull(raw);
        if (day == null || !calendar.isPlayable(day)) throw notFound();
        return day;
    }

    private static LocalDate parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String siteUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No puzzle for that day");
    }
}
