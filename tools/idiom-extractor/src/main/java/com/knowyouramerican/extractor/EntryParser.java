package com.knowyouramerican.extractor;

import com.knowyouramerican.extractor.PdfLines.FontKind;
import com.knowyouramerican.extractor.PdfLines.Line;
import com.knowyouramerican.extractor.PdfLines.Run;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns typeset lines into {@link Entry entries}.
 *
 * <p>Layout facts (verified across all dictionary pages): entry heads start at x = 36/54 (left
 * column) or 213/231 (right column) depending on page parity, and continuation lines are indented
 * exactly 9pt further. Inside an entry the font tells us what each run is (see {@link FontKind}).
 */
final class EntryParser {

    private static final float[] HEAD_X = {36f, 54f, 213f, 231f};
    private static final Pattern SENSE_NUMBER = Pattern.compile("^\\s*(\\d+)\\.\\s*$");
    private static final Pattern SENSE_NUMBER_PREFIX = Pattern.compile("^\\s*(\\d+)\\.\\s*");
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^\\s*(a|an|the)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSE_NUMBER_IN_HEAD = Pattern.compile("(?:^|\\s)(\\d+)\\.(?:\\s|$)");
    /** Soft hyphen placeholder for a hyphen at the end of a line, resolved once text is joined. */
    private static final char LINE_HYPHEN = '\u00AD';
    /** Marks a small-caps "and" between sense-specific forms. */
    private static final String FORM_SEPARATOR = "|";

    private final Set<String> hyphenatedWords;

    EntryParser(Set<String> hyphenatedWords) {
        // Also index adjacent pairs, so "high-and-mighty" broken as "high-and-|mighty" keeps its hyphen.
        Set<String> pairs = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String w : hyphenatedWords) {
            String[] parts = w.split("-");
            for (int i = 0; i + 1 < parts.length; i++) pairs.add(parts[i] + "-" + parts[i + 1]);
        }
        this.hyphenatedWords = pairs;
    }

    static boolean isHeadLine(Line line) {
        for (float hx : HEAD_X) {
            if (Math.abs(line.x() - hx) < 4f) return true; // articles ("a labor of love") sit ~3pt in
        }
        return false;
    }

    List<Entry> parse(List<Line> lines) {
        List<Entry> entries = new ArrayList<>();
        List<Run> current = null;
        int page = 0;
        for (Line line : lines) {
            if (isHeadLine(line)) {
                if (current != null) entries.add(parseEntry(current, page));
                current = new ArrayList<>();
                page = line.page();
            } else if (current == null) {
                continue; // continuation before the first head (section start)
            }
            appendLine(current, line);
        }
        if (current != null) entries.add(parseEntry(current, page));
        // "[a]", "[an]", "[the]" are section notes about alphabetising, not idioms.
        return entries.stream()
                .filter(e -> !e.headword().isBlank() && !e.headword().startsWith("["))
                .toList();
    }

    /** Appends a line's runs, marking a trailing line-break hyphen on the previous run. */
    private static void appendLine(List<Run> acc, Line line) {
        if (!acc.isEmpty()) {
            Run last = acc.getLast();
            String t = last.text().stripTrailing();
            if (t.endsWith("-") && t.length() > 1 && Character.isLetter(t.charAt(t.length() - 2))) {
                acc.set(acc.size() - 1, new Run(last.kind(), t.substring(0, t.length() - 1) + LINE_HYPHEN));
            } else {
                acc.set(acc.size() - 1, new Run(last.kind(), last.text() + " "));
            }
        }
        acc.addAll(line.runs());
    }

    // ---------------------------------------------------------------- entry state machine

    private Entry parseEntry(List<Run> runs, int page) {
        List<String> forms = new ArrayList<>();
        StringBuilder form = new StringBuilder();
        List<SenseBuilder> senses = new ArrayList<>();
        SenseBuilder sense = null;
        boolean inHead = true;

        for (Run run : runs) {
            String text = run.text();
            FontKind kind = run.kind();
            if (kind == FontKind.OTHER) continue;
            // The boxed "T" (transposable object) introduces an example, just like a bullet.
            if (kind == FontKind.TMARK) {
                if (inHead) continue;
                kind = FontKind.BULLET;
            }

            if (inHead) {
                switch (kind) {
                    case HEAD, PLACEHOLDER, XREF -> {
                        // A bold sense number ends the head: "after all 1. anyway; ..."
                        var m = SENSE_NUMBER_IN_HEAD.matcher(text);
                        if (kind == FontKind.HEAD && m.find() && (form.length() > 0 || m.start() > 0)) {
                            form.append(text, 0, m.start());
                            inHead = false;
                            sense = new SenseBuilder();
                            senses.add(sense);
                            String rest = text.substring(m.end());
                            if (!rest.isBlank()) sense.forms.append(rest); // "1. and xyz"
                            continue;
                        }
                        // Alternate forms in the head are separated by ";"
                        for (String piece : text.split("(?<=;)", -1)) {
                            if (piece.endsWith(";")) {
                                form.append(piece, 0, piece.length() - 1);
                                addForm(forms, form);
                            } else {
                                form.append(piece);
                            }
                        }
                    }
                    case SMALLCAPS -> addForm(forms, form); // "and" introduces an alternate form
                    case DEF -> {
                        // Initial articles are set in roman: "an aching heart", "the ..."
                        if (form.isEmpty() && LEADING_ARTICLE.matcher(text).matches()) {
                            form.append(text);
                            continue;
                        }
                        inHead = false;
                        sense = new SenseBuilder();
                        senses.add(sense);
                        sense.accept(kind, text);
                    }
                    default -> {
                        inHead = false;
                        sense = new SenseBuilder();
                        senses.add(sense);
                        sense.accept(kind, text);
                    }
                }
                continue;
            }

            // Body: bold sense numbers start new senses; bold text inside a sense is a
            // sense-specific alternate form ("2. and keep up with ...").
            if (kind == FontKind.HEAD && SENSE_NUMBER.matcher(text).matches()) {
                sense = new SenseBuilder();
                senses.add(sense);
                continue;
            }
            if (kind == FontKind.HEAD && SENSE_NUMBER_PREFIX.matcher(text).find()) {
                sense = new SenseBuilder();
                senses.add(sense);
                text = SENSE_NUMBER_PREFIX.matcher(text).replaceFirst("");
            }
            sense.accept(kind, text);
        }
        addForm(forms, form);

        if (forms.isEmpty()) return new Entry("", List.of(), List.of(), page);
        List<Entry.Sense> built = senses.stream()
                .map(this::buildSense)
                .filter(s -> s.definition().matches("(?s).*[A-Za-z]{2,}.*")) // drop "." leftovers
                .toList();
        return new Entry(forms.getFirst(), List.copyOf(forms.subList(1, forms.size())), built, page);
    }

    private void addForm(List<String> forms, StringBuilder form) {
        String f = clean(form.toString());
        f = f.replaceAll("^and\\s+", "").replaceAll("[;,]$", "").strip();
        if (!f.isEmpty()) forms.add(f);
        form.setLength(0);
    }

    private final class SenseBuilder {
        final StringBuilder forms = new StringBuilder();
        final StringBuilder definition = new StringBuilder();
        final StringBuilder trailing = new StringBuilder();
        final List<StringBuilder> examples = new ArrayList<>();

        void accept(FontKind kind, String text) {
            switch (kind) {
                case BULLET -> examples.add(new StringBuilder());
                case EXAMPLE -> {
                    if (examples.isEmpty()) definition.append(text); // italic words inside a definition
                    else examples.getLast().append(text);
                }
                case HEAD, PLACEHOLDER, SMALLCAPS -> {
                    // Before any definition text, bold/condensed words are this sense's own
                    // phrasing; the small-caps "and" separates alternatives.
                    if (examples.isEmpty() && definition.isEmpty()) {
                        forms.append(kind == FontKind.SMALLCAPS ? FORM_SEPARATOR : text);
                    } else {
                        target().append(text);
                    }
                }
                default -> {
                    if (!examples.isEmpty() && (text.strip().startsWith("(") || !trailing.isEmpty())) {
                        trailing.append(text); // "(Informal.)" after the examples
                    } else {
                        target().append(text); // DEF, XREF, PLACEHOLDER
                    }
                }
            }
        }

        /**
         * Roman text after the examples started is usually a word mentioned inside the example
         * ("I knew about the problem from the word go."). Parenthesised comments are notes.
         */
        private StringBuilder target() {
            return examples.isEmpty() ? definition : examples.getLast();
        }
    }

    private Entry.Sense buildSense(SenseBuilder b) {
        String def = clean(b.definition.toString());
        List<String> notes = new ArrayList<>();

        // [grammatical context] at the start → notes
        if (def.startsWith("[")) {
            int close = def.indexOf(']');
            if (close > 0) {
                notes.add(def.substring(0, close + 1));
                def = def.substring(close + 1).strip();
            }
        }
        // Top-level (parenthetical comments) → notes
        StringBuilder kept = new StringBuilder();
        int depth = 0;
        StringBuilder paren = new StringBuilder();
        for (char c : def.toCharArray()) {
            if (c == '(') {
                if (depth++ == 0) { paren.setLength(0); continue; }
            } else if (c == ')' && depth > 0) {
                if (--depth == 0) {
                    String inner = paren.toString().strip();
                    // "(something)", "(to someone)" are optional words of the definition;
                    // capitalised comments ("Informal.", "See also ...") are notes.
                    if (!inner.isEmpty() && Character.isLowerCase(inner.charAt(0))) kept.append('(').append(inner).append(')');
                    else notes.add(inner);
                    continue;
                }
            }
            if (depth > 0) paren.append(c); else kept.append(c);
        }
        def = kept.toString().replaceAll("\\s+", " ").replaceAll("\\s+([.;,:])", "$1").strip();
        // <explanation> brackets: keep the text
        if (def.startsWith("<") && def.contains(">")) {
            def = def.replace("<", "").replace(">", "").strip();
        }
        def = def.replaceAll("[;,]$", "").strip();

        String trailing = clean(b.trailing.toString());
        if (!trailing.isEmpty()) notes.add(trailing);

        List<String> examples = b.examples.stream()
                .map(sb -> clean(sb.toString()))
                .filter(s -> !s.isEmpty())
                .toList();
        List<String> senseForms = new ArrayList<>();
        for (String f : clean(b.forms.toString()).split("\\s*[;" + FORM_SEPARATOR + "]\\s*")) {
            f = f.replaceAll("[;,]$", "").strip();
            if (!f.isEmpty()) senseForms.add(f);
        }
        return new Entry.Sense(def, notes.isEmpty() ? null : String.join(" ", notes), examples,
                List.copyOf(senseForms));
    }

    /** Joins line-break hyphens, normalises whitespace and typography. */
    String clean(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == LINE_HYPHEN) {
                // word fragments around the break
                int start = out.length();
                while (start > 0 && Character.isLetter(out.charAt(start - 1))) start--;
                int j = i + 1;
                while (j < s.length() && s.charAt(j) == ' ') j++;
                int end = j;
                while (end < s.length() && Character.isLetter(s.charAt(end))) end++;
                String left = out.substring(start);
                String right = s.substring(j, end);
                boolean keepHyphen = hyphenatedWords.contains(left + "-" + right)
                        || (!right.isEmpty() && Character.isUpperCase(right.charAt(0)));
                if (keepHyphen) out.append('-');
                i = j; // skip the line-join space
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString()
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+([.;,:!?’”)])", "$1")
                .replaceAll("([(“])\\s+", "$1")
                .strip();
    }
}
