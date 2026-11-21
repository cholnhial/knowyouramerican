package com.knowyouramerican.extractor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rates how "colorful" an idiom is, 0..100, so the daily pool favours phrases like
 * "kick the bucket" over grammar entries like "according to something".
 *
 * <p>Signals, roughly in order of weight:
 * <ul>
 *   <li>Non-literal: the definition doesn't reuse the head's own words.</li>
 *   <li>Imagery: animals, body parts, food, weather, objects.</li>
 *   <li>Register: proverbs, clichés, folksy and slang notes.</li>
 *   <li>Shape: 3–7 words beats single words and two-word phrasal verbs; similes ("as X as Y") are fun.</li>
 *   <li>Penalties: placeholders (someone/something), no example sentences.</li>
 * </ul>
 */
final class FunScorer {

    /** Entries below this are left out of the seed entirely. */
    static final int MIN_SCORE = 30;

    static final Set<String> PLACEHOLDERS = Set.of(
            "someone", "something", "one", "one's", "oneself", "someone's", "somewhere",
            "somebody", "something's", "someplace", "sometime");

    static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "of", "to", "in", "on", "at", "by", "for", "with", "and", "or", "as",
            "is", "be", "it", "its", "it's", "from", "up", "out", "off", "into", "onto", "over",
            "about", "all", "no", "not", "so", "than", "that", "this", "too", "very", "one's",
            "get", "got", "go", "have", "has", "make", "take", "put", "give", "keep", "come", "do",
            "what", "who", "how", "when", "where", "there", "here", "own", "any", "some", "you",
            "your", "i", "my", "me", "we", "they", "he", "she", "his", "her", "their");

    private static final Set<String> PARTICLES = Set.of(
            "up", "down", "out", "off", "in", "on", "over", "away", "back", "through", "around",
            "about", "along", "by", "for", "with", "into", "after", "across", "against");

    private static final Set<String> IMAGERY = Set.copyOf(List.of(
            // animals
            "cat", "cats", "dog", "dogs", "horse", "horses", "bull", "bird", "birds", "pig", "pigs",
            "fish", "chicken", "chickens", "cow", "cows", "bee", "bees", "wolf", "bear", "rat",
            "monkey", "goose", "duck", "duckling", "hen", "hen's", "fox", "lion", "lion's", "mouse",
            "mice", "snake", "worm", "worms", "bug", "bat", "frog", "goat", "sheep", "lamb",
            "camel's", "elephant", "crow", "eagle", "hog", "mule", "ant", "ants", "owl", "beaver",
            // body
            "heart", "head", "hand", "hands", "foot", "feet", "nose", "eye", "eyes", "ear", "ears",
            "tooth", "teeth", "tongue", "neck", "arm", "leg", "legs", "lip", "lips", "chin",
            "thumb", "finger", "fingers", "toe", "toes", "skin", "bone", "bones", "blood", "back",
            "shoulder", "belly", "hair", "face", "elbow", "knee", "knees", "heel", "heels", "nail",
            // food
            "pie", "cake", "apple", "bread", "butter", "cheese", "egg", "eggs", "salt", "pepper",
            "milk", "tea", "beans", "potato", "peas", "pickle", "cookie", "candy", "honey", "nuts",
            "nut", "banana", "bananas", "onions", "meat", "soup", "jam", "gravy", "lemon", "cherry",
            "pudding", "carrot", "cucumber", "beet", "toast", "bacon", "dough", "fruit", "grapes",
            // nature / weather
            "fire", "ice", "moon", "sun", "storm", "rain", "snow", "wind", "thunder", "lightning",
            "sky", "sea", "ocean", "river", "stream", "rock", "rocks", "stone", "tree", "trees",
            "bush", "wood", "woods", "hill", "mountain", "mud", "dust", "cloud", "clouds", "hay",
            "grass", "flower", "flowers", "rose", "roses", "weather", "water", "hot", "cold",
            // objects & places
            "bucket", "bullet", "gun", "guns", "boat", "ship", "wheel", "horn", "horns", "devil",
            "ghost", "ball", "card", "cards", "hat", "shoe", "shoes", "boots", "bag", "pot", "kettle",
            "nail", "hammer", "rope", "bell", "bells", "whistle", "drum", "fiddle", "tune", "candle",
            "barn", "fence", "wall", "door", "roof", "window", "bridge", "road", "map",
            "clock", "penny", "dollar", "buck", "nickel", "dime", "gold", "silver", "iron", "brass",
            "glass", "paper", "pen", "book", "chips", "pins", "needles", "sword", "axe", "ax",
            "train", "wagon", "cart", "bandwagon", "saddle", "plate", "dish", "spoon", "knife",
            "cup", "barrel", "bottle", "box", "basket", "tail", "feather", "feathers", "nest",
            "dirt", "beach", "rug", "carpet", "pants", "shirt", "sleeve", "socks",
            "picture", "deck", "chip", "hook", "line", "sinker", "bait", "jar"));

    private FunScorer() {}

    static List<String> words(String headword) {
        return Arrays.stream(headword.toLowerCase(Locale.ROOT)
                        .replaceAll("\\(([^)]*)\\)", " ")   // optional words "(as) mad as a hatter"
                        .replace('’', '\'')
                        .split("[^a-z']+"))
                .filter(w -> !w.isBlank())
                .toList();
    }

    static List<String> contentWords(String headword) {
        return words(headword).stream()
                .filter(w -> !PLACEHOLDERS.contains(w) && !STOPWORDS.contains(w))
                .toList();
    }

    static int score(String headword, List<Entry.Sense> senses) {
        if (senses.isEmpty()) return 0;
        List<String> words = words(headword);
        List<String> content = contentWords(headword);
        if (content.isEmpty()) return 0; // pure grammar: "as for someone", "at that"

        int score = 50;

        // Shape
        int n = words.size();
        if (n == 1) score -= 25;
        else if (n == 2) score -= 5;
        else if (n <= 7) score += 8;
        if (n == 2 && PARTICLES.contains(words.get(1))) score -= 12; // phrasal verbs: "act up"
        if (headword.toLowerCase(Locale.ROOT).matches("^\\(?as\\)? \\w+ as .*")) score += 10; // similes

        // Placeholders make an entry feel like grammar, not a saying
        long placeholders = words.stream().filter(PLACEHOLDERS::contains).count();
        score -= (int) Math.min(30, placeholders * 10);
        if (placeholders > 0 && content.size() <= 1) score -= 15; // "according to something"

        // Proper names add flavour: "according to Hoyle", "keep up with the Joneses"
        if (Arrays.stream(headword.split("\\s+")).skip(1).anyMatch(w -> w.matches("[A-Z][a-z]+.*"))) score += 10;

        // Imagery
        if (words.stream().anyMatch(IMAGERY::contains)) score += 15;

        // Register (notes)
        String notes = senses.stream()
                .map(s -> s.notes() == null ? "" : s.notes())
                .reduce("", (a, b) -> a + " " + b)
                .toLowerCase(Locale.ROOT);
        if (notes.contains("proverb")) score += 18;
        else if (notes.contains("cliché") || notes.contains("folksy")) score += 12;
        else if (notes.contains("slang") || notes.contains("informal")) score += 6;
        if (notes.contains("rare") || notes.contains("formal.") && !notes.contains("informal")) score -= 10;

        // Literalness: definition reusing the head's content words → not idiomatic
        Entry.Sense first = senses.getFirst();
        String def = first.definition().toLowerCase(Locale.ROOT);
        long reused = content.stream().filter(w -> def.contains(stem(w))).count();
        double overlap = (double) reused / content.size();
        if (overlap >= 0.5) score -= 12;
        else if (overlap == 0) score += 10;

        // Playability: FITS questions need example sentences
        if (senses.stream().allMatch(s -> s.examples().isEmpty())) score -= 30;

        return Math.max(0, Math.min(100, score));
    }

    /** Crude stem so "average"/"averages", "hurry"/"hurried" match. */
    private static String stem(String w) {
        return w.length() > 5 ? w.substring(0, w.length() - 2) : w;
    }
}
