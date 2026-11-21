package com.knowyouramerican.web;

import java.util.Arrays;

/**
 * The player's choices for one day, as sent by the browser: {@code "2,0,-,-,-"} — one slot per
 * question, an option index 0..3 or {@code -} for unanswered. The browser's localStorage is the
 * source of truth; the server only reads this to render answered cards.
 */
public record Answers(int[] choices) {

    public static final int UNANSWERED = -1;
    public static final int SIZE = 5;

    public static Answers none() {
        int[] c = new int[SIZE];
        Arrays.fill(c, UNANSWERED);
        return new Answers(c);
    }

    /** Lenient: anything malformed counts as unanswered. */
    public static Answers parse(String raw) {
        Answers a = none();
        if (raw == null || raw.isBlank()) return a;
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < Math.min(parts.length, SIZE); i++) {
            try {
                int v = Integer.parseInt(parts[i].trim());
                if (v >= 0 && v <= 3) a.choices[i] = v;
            } catch (NumberFormatException ignored) {
                // "-" or junk → unanswered
            }
        }
        return a;
    }

    /** Choice for 1-based position, or {@link #UNANSWERED}. */
    public int at(int position) {
        return choices[position - 1];
    }

    public boolean answered(int position) {
        return at(position) != UNANSWERED;
    }

    public Answers with(int position, int choice) {
        int[] c = choices.clone();
        c[position - 1] = choice;
        return new Answers(c);
    }

    public int answeredCount() {
        return (int) Arrays.stream(choices).filter(c -> c != UNANSWERED).count();
    }

    /** First unanswered position, or 0 when all are done. */
    public int firstOpen() {
        for (int p = 1; p <= SIZE; p++) {
            if (!answered(p)) return p;
        }
        return 0;
    }

    @Override
    public String toString() {
        return String.join(",", Arrays.stream(choices).mapToObj(c -> c == UNANSWERED ? "-" : String.valueOf(c)).toList());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Answers other && Arrays.equals(choices, other.choices);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(choices);
    }
}
