package com.knowyouramerican.puzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowyouramerican.idiom.Idiom;
import com.knowyouramerican.support.SeedPool;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PuzzleGeneratorTest {

    static final int DAYS = 400;
    static List<Idiom> pool;
    static PuzzleGenerator generator;
    static Map<LocalDate, List<Question>> season = new LinkedHashMap<>();

    /** Generate 400 consecutive days once, threading usage like PuzzleService does. */
    @BeforeAll
    static void generateSeason() {
        pool = SeedPool.load();
        generator = new PuzzleGenerator(pool);
        Map<Long, LocalDate> usage = new HashMap<>();
        LocalDate day = PuzzleCalendar.FIRST_DAY;
        for (int i = 0; i < DAYS; i++, day = day.plusDays(1)) {
            List<Question> qs = generator.generate(day, usage);
            season.put(day, qs);
            for (Question q : qs) usage.put(q.idiomId(), day);
        }
    }

    @Test
    void everyDayHasFiveQuestionsWithFourDistinctOptionsAndOneCorrect() {
        season.forEach((day, qs) -> {
            assertThat(qs).as(day.toString()).hasSize(5);
            assertThat(qs).extracting(Question::position).containsExactly(1, 2, 3, 4, 5);
            for (Question q : qs) {
                assertThat(q.options()).as("%s q%d", day, q.position()).hasSize(4);
                assertThat(q.options().stream().map(Phrases::normalize).distinct()).hasSize(4);
                assertThat(q.correctIndex()).isBetween(0, 3);
                assertThat(q.options()).allSatisfy(o -> assertThat(o).isNotBlank());
            }
        });
    }

    @Test
    void noDuplicateIdiomsWithinADayAndNoRepeatsWhilePoolLasts() {
        Set<Long> seenEver = new HashSet<>();
        season.forEach((day, qs) -> {
            Set<Long> ids = new HashSet<>();
            qs.forEach(q -> ids.add(q.idiomId()));
            assertThat(ids).as(day.toString()).hasSize(5);
            assertThat(ids.stream().filter(seenEver::contains)).as("repeat on " + day).isEmpty();
            seenEver.addAll(ids);
        });
    }

    @Test
    void deterministicForAGivenDate() {
        LocalDate day = LocalDate.of(2026, 7, 4);
        assertThat(generator.generate(day, Map.of())).isEqualTo(generator.generate(day, Map.of()));
        assertThat(new PuzzleGenerator(pool).generate(day, Map.of())).isEqualTo(generator.generate(day, Map.of()));
    }

    @Test
    void meaningRarelyGivesItselfAway() {
        // Echoing definitions are routed to FINISH/FITS when possible; a few can't be.
        long echoing = questions(QuestionType.MEANING).stream().filter(q -> PuzzleGenerator.echoesHeadword(idiom(q))).count();
        assertThat(echoing).isLessThan(questions(QuestionType.MEANING).size() / 20);
    }

    @Test
    void meaningQuestionsShowTheIdiomAndItsOwnDefinition() {
        questions(QuestionType.MEANING).forEach(q -> {
            Idiom idiom = idiom(q);
            assertThat(q.prompt()).isEqualTo(idiom.headword());
            assertThat(q.options().get(q.correctIndex()))
                    .isEqualTo(PuzzleGenerator.displayDefinition(idiom.definition()));
            // All options share the answer's capitalisation, so case doesn't give it away.
            boolean upper = Character.isUpperCase(q.options().get(q.correctIndex()).charAt(0));
            assertThat(q.options()).allMatch(o -> Character.isUpperCase(o.charAt(0)) == upper);
        });
    }

    @Test
    void finishQuestionsBlankOneWordAndNeverLeakIt() {
        questions(QuestionType.FINISH).forEach(q -> {
            String answer = q.options().get(q.correctIndex());
            assertThat(q.prompt()).contains(Question.BLANK);
            assertThat(q.prompt().replace(Question.BLANK, answer)).isEqualTo(idiom(q).headword());
            assertThat(Phrases.words(q.prompt())).doesNotContain(Phrases.normalize(answer));
            assertThat(Phrases.contentWords(q.prompt().replace(Question.BLANK, " "))).as("clue left in " + q.prompt()).isNotEmpty();
        });
    }

    @Test
    void fitsQuestionsBlankTheIdiomInAnExampleAndNeverLeakIt() {
        questions(QuestionType.FITS).forEach(q -> {
            Idiom idiom = idiom(q);
            assertThat(q.prompt()).contains(Question.BLANK);
            assertThat(Phrases.words(q.prompt())).as("context around the blank").hasSizeGreaterThanOrEqualTo(5);
            assertThat(q.options().get(q.correctIndex())).isEqualTo(idiom.headword());
            // The idiom's rarest word must not survive in the sentence.
            List<String> content = Phrases.contentWords(idiom.headword());
            assertThat(content.stream().anyMatch(w -> !Phrases.words(q.prompt()).contains(w)))
                    .as("%s → %s", idiom.headword(), q.prompt()).isTrue();
        });
    }

    @Test
    void daysMixQuestionTypesAndTheHardTypesUsuallySucceed() {
        Map<QuestionType, Integer> totals = new EnumMap<>(QuestionType.class);
        season.forEach((day, qs) -> {
            Set<QuestionType> types = new HashSet<>();
            qs.forEach(q -> { types.add(q.type()); totals.merge(q.type(), 1, Integer::sum); });
            assertThat(types).as(day.toString()).hasSizeGreaterThanOrEqualTo(2);
        });
        int all = DAYS * 5;
        // Plan is 2 MEANING + ~1.5 FINISH + ~1.5 FITS; fallbacks to MEANING should stay modest.
        assertThat(totals.get(QuestionType.FINISH)).isGreaterThan(all / 5);
        assertThat(totals.get(QuestionType.FITS)).isGreaterThan(all / 5);
    }

    @Test
    void blankIdiomHandlesInflectionPlaceholdersAndOptionalWords() {
        assertThat(PuzzleGenerator.blankIdiom("I didn’t want to go, but I bit the bullet and went.", "bite the bullet"))
                .contains("I didn’t want to go, but I _____ and went.");
        assertThat(PuzzleGenerator.blankIdiom("We patted Ann on the back for a good performance.", "pat someone on the back"))
                .contains("We _____ for a good performance.");
        assertThat(PuzzleGenerator.blankIdiom("The penknife is sharp as a razor.", "(as) sharp as a razor"))
                .contains("The penknife is _____.");
        assertThat(PuzzleGenerator.blankIdiom("It’s raining cats and dogs. Look at it pour!", "rain cats and dogs"))
                .contains("It’s _____. Look at it pour!");
        assertThat(PuzzleGenerator.blankIdiom("Nothing to see here.", "rain cats and dogs")).isEmpty();
        // Trailing slots stay outside the blank
        assertThat(PuzzleGenerator.blankIdiom("Are you rooting for me, or are you just shouting?", "root for someone or something"))
                .contains("Are you _____ me, or are you just shouting?");
        assertThat(PuzzleGenerator.blankIdiom("The young boy has the makings of a fine baseball player.", "have the makings of something"))
                .contains("The young boy _____ a fine baseball player.");
    }

    /** Not a test: prints a few days for eyeballing (`-Dkya.print=true`). */
    @Test
    void printSample() {
        if (!Boolean.getBoolean("kya.print")) return;
        season.entrySet().stream().skip(Long.getLong("kya.skip", 320)).limit(Long.getLong("kya.days", 4)).forEach(e -> {
            System.out.println("\n=== " + e.getKey());
            for (Question q : e.getValue()) {
                System.out.printf("%d. [%s] %s%n", q.position(), q.type(), q.prompt());
                for (int i = 0; i < 4; i++) {
                    System.out.printf("     %s %s%n", i == q.correctIndex() ? "✓" : " ", q.options().get(i));
                }
            }
        });
    }

    private List<Question> questions(QuestionType type) {
        return season.values().stream().flatMap(List::stream).filter(q -> q.type() == type).toList();
    }

    private Idiom idiom(Question q) {
        return generator.idiom(q.idiomId());
    }
}
