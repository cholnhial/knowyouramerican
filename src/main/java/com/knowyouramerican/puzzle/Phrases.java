package com.knowyouramerican.puzzle;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Word-level helpers for idiom headwords and sentences. */
final class Phrases {

    /** Generic slots in dictionary heads ("pat someone on the back"). */
    static final Set<String> PLACEHOLDERS = Set.of(
            "someone", "something", "one", "one's", "oneself", "someone's", "somewhere",
            "somebody", "something's", "someplace", "sometime");

    static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "of", "to", "in", "on", "at", "by", "for", "with", "and", "or", "as",
            "is", "be", "it", "its", "it's", "from", "up", "out", "off", "into", "onto", "over",
            "about", "all", "no", "not", "so", "than", "that", "this", "too", "very", "but",
            "what", "who", "how", "when", "where", "there", "here", "own", "any", "some", "you",
            "your", "i", "my", "me", "we", "they", "he", "she", "his", "her", "their", "them",
            "can't", "don't", "won't", "isn't", "are", "was", "were", "has", "have", "had",
            "will", "would", "could", "should", "just", "like", "more", "most", "much", "make",
            "get", "go", "take", "put", "give", "keep", "come", "do", "let", "set", "run");

    private Phrases() {}

    /** Lower-cased words with straight apostrophes; optional "(…)" words removed. */
    static List<String> words(String text) {
        return Arrays.stream(normalize(text).replaceAll("\\([^)]*\\)", " ").split("[^a-z']+"))
                .map(w -> w.replaceAll("^'+|'+$", ""))
                .filter(w -> !w.isBlank())
                .toList();
    }

    /** Words that carry meaning: no placeholders, no stopwords, at least 3 letters. */
    static List<String> contentWords(String text) {
        return words(text).stream()
                .filter(w -> w.length() >= 3 && !PLACEHOLDERS.contains(w) && !STOPWORDS.contains(w))
                .toList();
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'');
    }

    /** Rough word class by ending, so distractor words look like the answer. */
    static String shape(String word) {
        String w = normalize(word);
        if (w.endsWith("ing")) return "ing";
        if (w.endsWith("ed")) return "ed";
        if (w.endsWith("ly")) return "ly";
        if (w.endsWith("'s")) return "poss";
        if (w.endsWith("s") && !w.endsWith("ss")) return "s";
        return "base";
    }

    /** Re-apply the answer's capitalisation to a distractor ("Curiosity" → "Patience"). */
    static String matchCase(String like, String word) {
        if (!like.isEmpty() && Character.isUpperCase(like.charAt(0)) && !word.isEmpty()) {
            return Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return word;
    }
}
