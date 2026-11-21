package com.knowyouramerican.extractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Reads the dictionary pages as typeset lines. Each line keeps its runs of same-font text and the
 * x position of its first glyph, which is what tells a new entry (hanging indent) from a
 * continuation line. PDF content order is left column, then right column, then the running head.
 */
final class PdfLines {

    /** A run of text in one font. */
    record Run(FontKind kind, String text) {}

    /** One typeset line. */
    record Line(int page, float x, float y, List<Run> runs) {
        String text() {
            StringBuilder sb = new StringBuilder();
            runs.forEach(r -> sb.append(r.text()));
            return sb.toString();
        }
    }

    /** What a font means in this book's typography. */
    enum FontKind {
        HEAD,        // Formata-Medium: entry heads, alternate forms, sense numbers
        PLACEHOLDER, // Formata-Condensed / LightCondensed: someone, something, one's
        XREF,        // Formata-Regular: cross-references, initial articles, running heads
        DEF,         // Minion-Regular: definitions and comments
        SMALLCAPS,   // Minion-RegularSC: the "and" joining alternate forms
        EXAMPLE,     // Minion-Italic: example sentences
        BULLET,      // MathematicalPi: the box before each example
        TMARK,       // FormataBoxedTReg: transitive "T" marker, ignored
        OTHER;

        static FontKind of(String fontName) {
            String f = fontName.substring(fontName.indexOf('+') + 1);
            if (f.equals("Formata-Medium") || f.equals("Formata-MediumItalic")) return HEAD;
            if (f.startsWith("Formata-") && f.contains("Condensed")) return PLACEHOLDER;
            if (f.equals("Formata-Regular")) return XREF;
            if (f.equals("Minion-Regular")) return DEF;
            if (f.equals("Minion-RegularSC")) return SMALLCAPS;
            if (f.equals("Minion-Italic")) return EXAMPLE;
            if (f.startsWith("MathematicalPi")) return BULLET;
            if (f.startsWith("FormataBoxed")) return TMARK;
            return OTHER;
        }
    }

    /** Running head at the top and page number at the bottom sit outside this band. */
    private static final float BODY_TOP = 55f;
    private static final float BODY_BOTTOM = 610f;

    private static final Pattern MID_LINE_HYPHENATED =
            Pattern.compile("\\b([A-Za-z]+(?:-[A-Za-z]+)+)\\b(?!$)");

    private final List<Line> lines = new ArrayList<>();
    /** Words seen hyphenated in the middle of a line: real compounds, not line-break hyphens. */
    private final Set<String> hyphenatedWords = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private PdfLines() {}

    static PdfLines read(PDDocument doc, int firstPage, int lastPage) throws IOException {
        PdfLines out = new PdfLines();
        Collector c = out.new Collector();
        c.setSortByPosition(false);
        c.setStartPage(firstPage);
        c.setEndPage(lastPage);
        c.getText(doc);
        c.flushLine();
        return out;
    }

    List<Line> lines() {
        return lines;
    }

    Set<String> hyphenatedWords() {
        return hyphenatedWords;
    }

    private final class Collector extends PDFTextStripper {
        private final List<Run> runs = new ArrayList<>();
        private final StringBuilder buf = new StringBuilder();
        private FontKind kind;
        private float x = -1, y = -1;

        Collector() throws IOException {
            super();
        }

        /** Last glyph written on this line, to measure the gap before the next word. */
        private TextPosition prev;

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            if (positions.isEmpty()) return;
            // PDFBox doesn't always break lines between the running head, the page number and
            // the first body line of the next page; split on any vertical jump.
            if (prev != null && Math.abs(positions.getFirst().getYDirAdj() - y) > 3f) {
                flushLine();
            }
            if (prev != null && !isLigatureSplit(prev, positions.getFirst())) {
                buf.append(' ');
            }
            for (TextPosition tp : positions) {
                FontKind k = FontKind.of(tp.getFont().getName());
                if (x < 0) {
                    x = tp.getXDirAdj();
                    y = tp.getYDirAdj();
                }
                if (k != kind) {
                    flushRun();
                    kind = k;
                }
                buf.append(tp.getUnicode());
            }
            prev = positions.getLast();
        }

        /**
         * The "fl" ligature is set as an "f" glyph followed ~1pt later by "l"; PDFBox reads that as
         * a word break ("f lame"). Real word spaces in this book are 2.3pt or more.
         */
        private static boolean isLigatureSplit(TextPosition before, TextPosition after) {
            float gap = after.getXDirAdj() - (before.getXDirAdj() + before.getWidthDirAdj());
            return before.getUnicode().endsWith("f") && gap < 1.6f;
        }

        @Override
        protected void writeLineSeparator() {
            flushLine();
        }

        @Override
        protected void writePageStart() {
            flushLine();
        }

        @Override
        protected void writePageEnd() {
            flushLine();
        }

        private void flushRun() {
            // A run boundary may fall right after a word: keep the trailing space with the run.
            if (buf.length() > 0 && kind != null) {
                runs.add(new Run(kind, buf.toString()));
            }
            buf.setLength(0);
        }

        void flushLine() {
            flushRun();
            if (!runs.isEmpty() && y >= BODY_TOP && y <= BODY_BOTTOM) {
                Line line = new Line(getCurrentPageNo(), x, y, List.copyOf(runs));
                lines.add(line);
                Matcher m = MID_LINE_HYPHENATED.matcher(line.text().strip());
                while (m.find()) {
                    hyphenatedWords.add(m.group(1));
                }
            }
            runs.clear();
            prev = null;
            kind = null;
            x = -1;
            y = -1;
        }
    }
}
