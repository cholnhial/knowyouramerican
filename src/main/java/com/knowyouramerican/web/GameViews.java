package com.knowyouramerican.web;

import com.knowyouramerican.idiom.Idiom;
import com.knowyouramerican.idiom.IdiomRepository;
import com.knowyouramerican.puzzle.PuzzleCalendar;
import com.knowyouramerican.puzzle.PuzzleGenerator;
import com.knowyouramerican.puzzle.PuzzleRepository;
import com.knowyouramerican.puzzle.PuzzleService;
import com.knowyouramerican.puzzle.Question;
import com.knowyouramerican.puzzle.QuestionType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Builds {@link GameView}s from stored puzzles plus the browser's answers. */
@Component
public class GameViews {

    static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    static final DateTimeFormatter SHARE_DAY = DateTimeFormatter.ofPattern("EEE MMM d, yyyy", Locale.US);

    private final PuzzleRepository puzzles;
    private final IdiomRepository idioms;
    private final PuzzleService service;
    private final PuzzleCalendar calendar;

    public GameViews(PuzzleRepository puzzles, IdiomRepository idioms, PuzzleService service,
                     PuzzleCalendar calendar) {
        this.puzzles = puzzles;
        this.idioms = idioms;
        this.service = service;
        this.calendar = calendar;
    }

    /** The day's questions, generating the day on demand if the scheduler hasn't yet. */
    public Optional<List<Question>> questions(LocalDate day) {
        if (!calendar.isPlayable(day)) return Optional.empty();
        List<Question> qs = puzzles.findQuestions(day);
        if (qs.isEmpty()) {
            service.ensureThrough(day);
            qs = puzzles.findQuestions(day);
        }
        return qs.isEmpty() ? Optional.empty() : Optional.of(qs);
    }

    /**
     * @param position     card to show (1..5), or 0 for "first unanswered, else summary"
     * @param justAnswered position answered by this request (animations), or 0
     * @param playerToday  the browser's local date (for "Today's five" and next-day links)
     */
    public GameView build(LocalDate day, List<Question> questions, Answers answers, int position,
                          int justAnswered, LocalDate playerToday, String siteUrl) {
        List<Idiom> dayIdioms = questions.stream()
                .map(q -> idioms.findById(q.idiomId()).orElseThrow())
                .toList();

        List<String> results = new ArrayList<>();
        List<CardView> review = new ArrayList<>();
        int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            int p = q.position();
            if (!answers.answered(p)) {
                results.add("pending");
            } else if (answers.at(p) == q.correctIndex()) {
                results.add("correct");
                score++;
            } else {
                results.add("missed");
            }
            review.add(card(q, dayIdioms.get(i), answers, p == justAnswered));
        }

        int done = answers.answeredCount();
        boolean finished = done == Answers.SIZE;
        int shown = position >= 1 && position <= Answers.SIZE ? position : answers.firstOpen();
        CardView card = shown == 0 ? null : review.get(shown - 1);

        LocalDate today = playerToday != null && calendar.isPlayable(playerToday) ? playerToday : calendar.utcToday();
        LocalDate prev = day.minusDays(1);
        LocalDate next = day.plusDays(1);
        return new GameView(
                day,
                DAY_LABEL.format(day),
                day.equals(today),
                results,
                done,
                score,
                card,
                nextOpen(answers, shown),
                finished,
                review,
                finished ? shareText(day, results, score, siteUrl) : null,
                answers.toString(),
                prev.isBefore(PuzzleCalendar.FIRST_DAY) ? null : prev,
                next.isAfter(today) ? null : next);
    }

    /** The next unanswered position after {@code from} (wrapping), or 0 → summary. */
    static int nextOpen(Answers answers, int from) {
        for (int k = 1; k <= Answers.SIZE; k++) {
            int p = (from + k - 1) % Answers.SIZE + 1;
            if (!answers.answered(p)) return p;
        }
        return 0;
    }

    CardView card(Question q, Idiom idiom, Answers answers, boolean justAnswered) {
        boolean answered = answers.answered(q.position());
        List<String> parts = q.type() == QuestionType.MEANING
                ? List.of(q.prompt())
                : Arrays.asList(q.prompt().split(Pattern.quote(Question.BLANK), -1));
        String instruction = switch (q.type()) {
            case MEANING -> "Pick the meaning.";
            case FINISH -> "Pick the missing word.";
            case FITS -> "Pick the idiom that completes the sentence.";
        };
        String hint = q.type() == QuestionType.FINISH
                ? PuzzleGenerator.displayDefinition(idiom.definition())
                : null;
        CardView.Reveal reveal = answered
                ? new CardView.Reveal(
                        idiom.headword(),
                        PuzzleGenerator.displayDefinition(idiom.definition()),
                        idiom.examples().isEmpty() ? null : idiom.examples().getFirst(),
                        idiom.notes())
                : null;
        return new CardView(
                q.position(),
                q.type(),
                q.type().label(),
                instruction,
                parts,
                hint,
                q.options(),
                answered,
                answers.at(q.position()),
                answered ? q.correctIndex() : -1,
                justAnswered,
                reveal);
    }

    static String shareText(LocalDate day, List<String> results, int score, String siteUrl) {
        StringBuilder squares = new StringBuilder();
        for (String r : results) squares.append("correct".equals(r) ? "🟩" : "🟥");
        return "Know Your American · " + SHARE_DAY.format(day) + "\n"
                + squares + " " + score + "/5\n"
                + siteUrl;
    }
}
