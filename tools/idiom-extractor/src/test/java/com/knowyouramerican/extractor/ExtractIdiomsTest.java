package com.knowyouramerican.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowyouramerican.extractor.ExtractIdioms.SeedIdiom;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Golden tests against the real docs/idioms.pdf. Runs the extraction once (~10s). */
class ExtractIdiomsTest {

    static ExtractIdioms.Result result;
    static Map<String, SeedIdiom> byHead;

    @BeforeAll
    static void extract() throws Exception {
        result = ExtractIdioms.extract(Path.of(System.getProperty("kya.pdf", "../../docs/idioms.pdf")));
        byHead = result.idioms().stream()
                .collect(Collectors.toMap(SeedIdiom::headword, Function.identity()));
    }

    @Test
    void abovePar() {
        SeedIdiom i = byHead.get("above par");
        assertThat(i.senses()).hasSize(1);
        assertThat(i.senses().getFirst().definition()).isEqualTo("better than average or normal.");
        assertThat(i.senses().getFirst().examples()).containsExactly(
                "His work is above par, so he should get paid better.",
                "Your chances of winning the game are a little above par.");
    }

    @Test
    void accordingToHoyle_notesSeparatedFromDefinition() {
        Entry.Sense s = byHead.get("according to Hoyle").senses().getFirst();
        assertThat(s.definition()).isEqualTo("according to the rules; in keeping with the way it is normally done.");
        assertThat(s.notes()).startsWith("Refers to the rules for playing games. Edmond Hoyle wrote a book");
        assertThat(s.examples()).hasSize(2);
        assertThat(s.examples().get(1)).isEqualTo(
                "The carpenter said, “This is the way to drive a nail, according to Hoyle.”");
    }

    @Test
    void acidTest_lineBreakHyphenJoined() {
        Entry.Sense s = byHead.get("acid test").senses().getFirst();
        assertThat(s.definition()).isEqualTo("a test whose findings are beyond doubt or dispute.");
        assertThat(s.examples().get(1)).isEqualTo(
                "The senator isn’t very popular just now, but the acid test will be if he gets reelected.");
    }

    @Test
    void realCompoundHyphensSurviveLineBreaks() {
        assertThat(byHead.get("act high-and-mighty").senses().getFirst().examples())
                .allMatch(x -> x.contains("high-and-mighty"));
    }

    @Test
    void numberedSensesAreSplitAndNotPartOfHeadword() {
        SeedIdiom i = byHead.get("after all");
        assertThat(i.senses()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(byHead.keySet()).noneMatch(h -> h.matches(".*\\s\\d+\\.$"));
    }

    @Test
    void alternateFormsCaptured() {
        assertThat(byHead.get("aboveboard").altForms())
                .containsExactly("honest and aboveboard", "open and aboveboard");
    }

    @Test
    void leadingArticleKeptInHeadword() {
        assertThat(byHead).containsKeys("an aching heart", "an act of God");
    }

    @Test
    void senseSpecificFormsBecomeIdioms() {
        SeedIdiom i = byHead.get("kick the bucket");
        assertThat(i.senses().getFirst().definition()).isEqualTo("to die.");
        assertThat(i.senses().getFirst().examples()).allMatch(x -> x.contains("bucket"));
    }

    @Test
    void optionalObjectStaysInDefinition_capitalisedCommentGoesToNotes() {
        Entry.Sense s = byHead.get("bite the bullet").senses().getFirst();
        assertThat(s.definition()).isEqualTo("to put up with or endure (something).");
        assertThat(s.notes()).isEqualTo("Informal or slang.");
    }

    @Test
    void flLigaturesAreNotSplit() {
        assertThat(result.idioms())
                .flatMap(i -> i.senses().stream().flatMap(s -> s.examples().stream()).toList())
                .noneMatch(x -> x.matches(".*\\bf (loor|lower|lat|ly|lew|lood)\\b.*"));
    }

    @Test
    void crossReferencesAndSectionNotesAreExcluded() {
        assertThat(byHead).doesNotContainKeys("spill the beans", "[a]", "[an]");
        assertThat(result.crossRefs()).isGreaterThan(900);
        assertThat(result.idioms()).allSatisfy(i -> {
            assertThat(i.senses()).isNotEmpty();
            assertThat(i.senses()).allSatisfy(s -> assertThat(s.definition()).isNotBlank().doesNotStartWith("Go to "));
        });
    }

    @Test
    void funScoresRankColorfulAboveGrammatical() {
        assertThat(byHead.get("bite the bullet").funScore()).isGreaterThan(80);
        assertThat(byHead.get("let the cat out of the bag").funScore()).isGreaterThan(70);
        assertThat(byHead.get("according to something").funScore())
                .isLessThan(byHead.get("according to Hoyle").funScore());
        assertThat(result.idioms()).allMatch(i -> i.funScore() >= FunScorer.MIN_SCORE);
    }

    @Test
    void entryStartingAtTopOfPageIsNotLostWithRunningHead() {
        // "make way (for someone or something)" is the first line of PDF page 282.
        Entry.Sense s = byHead.get("make way (for someone or something)").senses().getFirst();
        assertThat(s.definition()).isEqualTo("to clear a path for someone or something.");
        assertThat(byHead.get("have contact with someone").senses().getFirst().examples())
                .contains("I have had no contact with Bill since he left town.");
    }

    @Test
    void italicHeadWordsAndRomanWordsInsideExamples() {
        SeedIdiom i = byHead.get("from the word go");
        assertThat(i.senses().getFirst().examples()).contains("I knew about the problem from the word go.");
        assertThat(i.senses().getFirst().notes()).isEqualTo("Informal.");
    }

    @Test
    void articleSlightlyIndentedStillStartsEntry() {
        assertThat(byHead).containsKey("a labor of love");
        assertThat(byHead.get("knuckle under (to someone or something)").senses().getFirst().examples())
                .containsExactly("You have to knuckle under to your boss if you expect to keep your job.",
                        "I’m too stubborn to knuckle under.");
    }

    @Test
    void transposedObjectMarkerStartsNewExample() {
        assertThat(byHead.get("carry something out").senses().getFirst().examples())
                .hasSizeGreaterThanOrEqualTo(2)
                .noneMatch(x -> x.matches(".*[.?!”][A-Z].*"));
    }

    @Test
    void totalInExpectedRange() {
        assertThat(result.totalEntries()).isBetween(6800, 7600);
        assertThat(result.idioms()).hasSizeBetween(5000, 6500);
    }
}
