package com.knowyouramerican.extractor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads docs/idioms.pdf and writes the seed file (JSON Lines, one idiom per line).
 *
 * <pre>usage: ExtractIdioms &lt;idioms.pdf&gt; &lt;out.jsonl&gt;</pre>
 */
public final class ExtractIdioms {

    /** PDF pages (1-based) that hold the dictionary proper; the Phrase-Finder Index follows. */
    static final int FIRST_PAGE = 16;
    static final int LAST_PAGE = 460;

    /** Seed record written to idioms.jsonl. */
    public record SeedIdiom(String headword, List<String> altForms, List<Entry.Sense> senses,
                            int funScore, int page) {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: ExtractIdioms <idioms.pdf> <out.jsonl>");
            System.exit(2);
        }
        Result r = extract(Path.of(args[0]));
        write(r.idioms(), Path.of(args[1]));
        System.out.printf(Locale.ROOT,
                "entries=%d crossRefOnly=%d duplicates=%d dropped(lowScore)=%d kept=%d → %s%n",
                r.totalEntries(), r.crossRefs(), r.duplicates(), r.lowScore(), r.idioms().size(), args[1]);
    }

    record Result(List<SeedIdiom> idioms, List<Entry> allEntries, int totalEntries,
                  int crossRefs, int duplicates, int lowScore) {}

    static Result extract(Path pdf) throws IOException {
        List<Entry> entries;
        try (PDDocument doc = Loader.loadPDF(new File(pdf.toString()))) {
            PdfLines lines = PdfLines.read(doc, FIRST_PAGE, LAST_PAGE);
            entries = new EntryParser(lines.hyphenatedWords()).parse(lines.lines());
        }

        // Sense-specific forms ("kick off ... 2. and kick the bucket to die") become idioms too.
        List<Entry> expanded = new ArrayList<>(entries);
        for (Entry e : entries) {
            for (Entry.Sense s : e.senses()) {
                if (!s.forms().isEmpty() && !s.definition().startsWith("Go to ")) {
                    expanded.add(new Entry(s.forms().getFirst(), s.forms().subList(1, s.forms().size()),
                            List.of(examplesUsing(s.forms().getFirst(), s)), e.page()));
                }
            }
        }

        int crossRefs = 0, duplicates = 0, lowScore = 0;
        Map<String, SeedIdiom> byHead = new LinkedHashMap<>();
        for (Entry e : expanded) {
            if (e.isCrossReferenceOnly()) {
                crossRefs++;
                continue;
            }
            List<Entry.Sense> senses = e.senses().stream()
                    .filter(s -> !s.definition().startsWith("Go to "))
                    .toList();
            int score = FunScorer.score(e.headword(), senses);
            if (score < FunScorer.MIN_SCORE) {
                lowScore++;
                continue;
            }
            String key = e.headword().toLowerCase(Locale.ROOT);
            if (byHead.containsKey(key)) {
                duplicates++;
                continue;
            }
            byHead.put(key, new SeedIdiom(e.headword(), e.altForms(), senses, score, e.page()));
        }
        return new Result(new ArrayList<>(byHead.values()), entries, entries.size(),
                crossRefs, duplicates, lowScore);
    }

    /**
     * A promoted sense shares examples with its parent ("kicked off" and "kick the bucket"). Keep
     * the ones that use the promoted phrasing's last content word, if any do.
     */
    static Entry.Sense examplesUsing(String form, Entry.Sense s) {
        String key = FunScorer.contentWords(form).stream().reduce((a, b) -> b).orElse(null);
        if (key == null) return s;
        List<String> using = s.examples().stream()
                .filter(x -> x.toLowerCase(Locale.ROOT).contains(key))
                .toList();
        return using.isEmpty() ? s : new Entry.Sense(s.definition(), s.notes(), using, s.forms());
    }

    static void write(List<SeedIdiom> idioms, Path out) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        JsonMapper json = JsonMapper.builder().build();
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (SeedIdiom i : idioms) {
                w.write(json.writeValueAsString(i));
                w.newLine();
            }
        }
    }
}
