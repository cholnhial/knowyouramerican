package com.knowyouramerican.puzzle;

import com.knowyouramerican.idiom.Idiom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a day's five questions from the idiom pool. Pure and deterministic: the same pool, usage
 * and date always give the same puzzle (all randomness comes from a date-seeded {@link Random}).
 *
 * <p>Guarantees (see PuzzleGeneratorTest): five different idioms per day, exactly four distinct
 * options per question with exactly one correct, and no question leaks its answer in the prompt.
 * Distractors are filtered so they can't also be right: they share no content words with the
 * answer's idiom or meaning.
 */
public final class PuzzleGenerator {

    public static final int QUESTIONS_PER_DAY = 5;
    /** How many attempts to find distractors before giving up on a question type. */
    private static final int MAX_TRIES = 4000;

    private final List<Idiom> pool;
    private final Map<Long, Idiom> byId = new HashMap<>();
    /** Content word → how many headwords contain it (rarer = more distinctive). */
    private final Map<String, Integer> wordFrequency = new HashMap<>();
    /** Every headword and alternate form, normalised, to avoid accidentally-correct distractors. */
    private final Set<String> knownPhrases = new HashSet<>();
    /**
     * FINISH distractor pools, chosen by the blank's context so wrong answers are the same kind
     * of word: nouns follow determiners ("the ___"), verbs start verb idioms ("___ the bullet").
     */
    private final List<String> nounWords;
    private final List<String> verbWords;
    private final List<String> lastWords;
    private final List<String> anyWords;

    private static final Set<String> DETERMINERS = Set.of(
            "a", "an", "the", "one's", "someone's", "his", "her", "my", "your", "their", "our",
            "of", "no", "every", "some", "any", "this", "that", "great", "big", "little", "old");

    public PuzzleGenerator(List<Idiom> pool) {
        this.pool = List.copyOf(pool);
        Set<String> nouns = new LinkedHashSet<>();
        Set<String> verbs = new LinkedHashSet<>();
        Set<String> last = new LinkedHashSet<>();
        Set<String> any = new LinkedHashSet<>();
        for (Idiom i : this.pool) {
            byId.put(i.id(), i);
            List<String> content = Phrases.contentWords(i.headword());
            new HashSet<>(content).forEach(w -> wordFrequency.merge(w, 1, Integer::sum));
            any.addAll(content);
            if (!content.isEmpty()) last.add(content.getLast());
            List<String> words = Phrases.words(i.headword());
            for (int k = 1; k < words.size(); k++) {
                if (DETERMINERS.contains(words.get(k - 1)) && content.contains(words.get(k))) nouns.add(words.get(k));
            }
            if (!words.isEmpty() && i.definition().startsWith("to ") && content.contains(words.getFirst())) {
                verbs.add(words.getFirst());
            }
            knownPhrases.add(phraseKey(i.headword()));
            i.altForms().forEach(f -> knownPhrases.add(phraseKey(f)));
        }
        this.nounWords = List.copyOf(nouns);
        this.verbWords = List.copyOf(verbs);
        this.lastWords = List.copyOf(last);
        this.anyWords = List.copyOf(any);
    }

    /**
     * Generates the puzzle for {@code date}.
     *
     * @param usage idiom id → date last used; read to avoid repeats (not modified)
     */
    public List<Question> generate(LocalDate date, Map<Long, LocalDate> usage) {
        Random rng = new Random(date.toEpochDay() * 0x9E3779B97F4A7C15L + 0x4B59);

        List<QuestionType> plan = new ArrayList<>(List.of(
                QuestionType.MEANING, QuestionType.MEANING, QuestionType.FINISH, QuestionType.FITS,
                rng.nextBoolean() ? QuestionType.FINISH : QuestionType.FITS));
        Collections.shuffle(plan, rng);

        // Phase 1: pick an idiom per slot that suits the slot's planned type. An idiom that
        // can't be a FINISH/FITS question is set aside and another drawn (up to 5 draws).
        Picker picker = new Picker(rng, usage);
        List<Idiom> picked = new ArrayList<>();
        for (int p = 0; p < QUESTIONS_PER_DAY; p++) {
            QuestionType type = plan.get(p);
            Idiom chosen = null;
            Idiom fallback = null;
            for (int attempt = 0; attempt < 5 && chosen == null; attempt++) {
                Idiom c = picker.draw(picked);
                boolean suits = type == QuestionType.MEANING && !echoesHeadword(c)
                        || hard(1, c, new HashSet<>(), new Random(0)).isPresent();
                if (suits) chosen = c;
                else if (fallback == null) fallback = c;
            }
            Idiom accepted = chosen != null ? chosen : fallback;
            picker.accept(accepted);
            picked.add(accepted);
        }

        // Phase 2: build the questions, now knowing the whole day (distractors avoid it).
        Set<Long> today = new HashSet<>();
        picked.forEach(i -> today.add(i.id()));
        List<Question> questions = new ArrayList<>();
        for (int p = 0; p < QUESTIONS_PER_DAY; p++) {
            Idiom idiom = picked.get(p);
            int position = p + 1;
            // Planned type first; a FINISH/FITS that can't be built tries the other, then MEANING.
            Optional<Question> q = switch (plan.get(p)) {
                case FINISH -> finish(position, idiom, rng).or(() -> fits(position, idiom, today, rng));
                case FITS -> fits(position, idiom, today, rng).or(() -> finish(position, idiom, rng));
                // A meaning that echoes the idiom's own words gives the answer away.
                case MEANING -> echoesHeadword(idiom) ? hard(position, idiom, today, rng) : Optional.empty();
            };
            questions.add(q.orElseGet(() -> meaning(position, idiom, today, rng)));
        }
        return questions;
    }

    private Optional<Question> hard(int position, Idiom idiom, Set<Long> today, Random rng) {
        return finish(position, idiom, rng).or(() -> fits(position, idiom, today, rng));
    }

    // ------------------------------------------------------------------ picking the five

    /** Weighted draws from the unused pool, with variety rules across the day. */
    private final class Picker {
        private final Random rng;
        private final List<Idiom> candidates;
        private final double[] cumulative;
        private final double total;
        private final Set<String> wordsToday = new HashSet<>();
        private boolean simileToday;

        Picker(Random rng, Map<Long, LocalDate> usage) {
            this.rng = rng;
            List<Idiom> unused = pool.stream()
                    .filter(i -> usage.get(i.id()) == null && playable(i))
                    .toList();
            if (unused.size() < 50) {
                // Pool exhausted: recycle the idioms that have rested longest.
                unused = pool.stream()
                        .filter(PuzzleGenerator::playable)
                        .sorted(Comparator.comparing((Idiom i) -> usage.getOrDefault(i.id(), LocalDate.MIN))
                                .thenComparingLong(Idiom::id))
                        .limit(Math.max(500, pool.size() / 5))
                        .toList();
            }
            this.candidates = unused;
            this.cumulative = new double[candidates.size()];
            double sum = 0;
            for (int i = 0; i < candidates.size(); i++) {
                double w = candidates.get(i).funScore() + 10;
                sum += w * w; // favour colorful idioms strongly, keep everything possible
                cumulative[i] = sum;
            }
            this.total = sum;
        }

        /** Next candidate that keeps the day varied: no shared words, one simile at most. */
        Idiom draw(List<Idiom> picked) {
            for (int tries = 0; tries < MAX_TRIES; tries++) {
                int idx = java.util.Arrays.binarySearch(cumulative, rng.nextDouble() * total);
                Idiom c = candidates.get(idx >= 0 ? idx : -idx - 1);
                if (picked.contains(c)) continue;
                if (Phrases.contentWords(c.headword()).stream().anyMatch(wordsToday::contains)) continue;
                if (simileToday && isSimile(c.headword())) continue;
                return c;
            }
            throw new IllegalStateException("Idiom pool too small to build a day");
        }

        void accept(Idiom c) {
            wordsToday.addAll(Phrases.contentWords(c.headword()));
            simileToday |= isSimile(c.headword());
        }
    }

    /** Guards against seed glitches: a definition needs real words. */
    private static boolean playable(Idiom i) {
        return i.definition() != null && i.definition().matches("(?s).*[A-Za-z]{3,}.*");
    }

    private static boolean isSimile(String headword) {
        return Phrases.normalize(headword).matches("^\\(?as\\)? \\w+ as .*");
    }

    // ------------------------------------------------------------------ MEANING

    private Question meaning(int position, Idiom idiom, Set<Long> today, Random rng) {
        String correct = displayDefinition(idiom.definition());
        if (correct.isEmpty()) correct = idiom.definition().strip();
        Set<String> avoid = new HashSet<>(Phrases.contentWords(correct));
        avoid.addAll(Phrases.contentWords(idiom.headword()));
        boolean verbPhrase = correct.startsWith("to ");
        int len = correct.length();

        List<String> distractors = new ArrayList<>();
        Set<String> seen = new HashSet<>(Set.of(Phrases.normalize(correct)));
        for (int t = 0; t < MAX_TRIES && distractors.size() < 3; t++) {
            Idiom other = pool.get(rng.nextInt(pool.size()));
            if (other.id() == idiom.id() || today.contains(other.id())) continue;
            String d = displayDefinition(other.definition());
            if (d.length() < 3 || d.startsWith("to ") != verbPhrase) continue;
            // Same "shape" as the answer: proverb meanings are capitalised sentences.
            if (Character.isUpperCase(d.charAt(0)) != Character.isUpperCase(correct.charAt(0))) continue;
            if (Math.abs(d.length() - len) > Math.max(20, len * 0.35)) continue;
            if (Phrases.contentWords(d).stream().anyMatch(avoid::contains)) continue;
            if (Phrases.contentWords(other.headword()).stream().anyMatch(avoid::contains)) continue;
            if (!seen.add(Phrases.normalize(d))) continue;
            distractors.add(d);
        }
        if (distractors.size() < 3) {
            throw new IllegalStateException("No meaning distractors for " + idiom.headword());
        }
        return assemble(position, idiom, QuestionType.MEANING, idiom.headword(), correct, distractors, rng);
    }

    /** True when at least half the idiom's content words appear in its own definition. */
    static boolean echoesHeadword(Idiom idiom) {
        List<String> head = Phrases.contentWords(idiom.headword());
        if (head.isEmpty()) return false;
        Set<String> def = new HashSet<>(Phrases.words(idiom.definition()));
        long shared = head.stream().filter(def::contains).count();
        return shared * 2 >= head.size();
    }

    /** Definitions as shown on a button: no trailing period, long ones cut at the first ";". */
    static String displayDefinition(String definition) {
        String d = definition.strip();
        if (d.length() > 90 && d.contains(";")) d = d.substring(0, d.indexOf(';'));
        d = d.replaceAll("[.;,:]+$", "").strip();
        return d;
    }

    // ------------------------------------------------------------------ FINISH

    Optional<Question> finish(int position, Idiom idiom, Random rng) {
        List<String> content = Phrases.contentWords(idiom.headword());
        // "___ out" or "get ___" is guesswork; need at least one other meaningful word as a clue.
        if (content.size() < 2) return Optional.empty();

        // Blank the most distinctive word: rarest across all headwords, then longest. Similes
        // blank the comparison ("(as) stubborn as a ___"). Contractions are never blanked.
        List<String> blankable = content.stream().filter(w -> !w.contains("'")).toList();
        if (blankable.isEmpty()) return Optional.empty();
        String answer = isSimile(idiom.headword())
                ? blankable.getLast()
                : blankable.stream()
                        .min(Comparator.comparingInt((String w) -> wordFrequency.getOrDefault(w, 0))
                                .thenComparing(Comparator.comparingInt(String::length).reversed()))
                        .orElseThrow();
        Pattern word = wordPattern(answer);
        Matcher m = word.matcher(Phrases.normalize(idiom.headword()));
        if (!m.find()) return Optional.empty();
        String original = idiom.headword().substring(m.start(), m.end());
        String prompt = word.matcher(Phrases.normalize(idiom.headword())).replaceAll(Question.BLANK);
        // Keep the original casing/typography outside the blank.
        prompt = restoreCase(idiom.headword(), prompt);
        // A clue must remain: "through and through" → "___ and ___" is unanswerable.
        if (Phrases.contentWords(prompt.replace(Question.BLANK, " ")).isEmpty()) return Optional.empty();

        List<String> source = distractorPool(idiom, answer, content);
        String shape = Phrases.shape(answer);
        Set<String> inHeadword = new HashSet<>(content);

        List<String> distractors = new ArrayList<>();
        for (int t = 0; t < MAX_TRIES && distractors.size() < 3; t++) {
            String w = source.get(rng.nextInt(source.size()));
            if (w.equals(answer) || inHeadword.contains(w) || distractors.contains(w)) continue;
            if (!Phrases.shape(w).equals(shape) || Math.abs(w.length() - answer.length()) > 3) continue;
            String candidatePhrase = phraseKey(idiom.headword().replaceAll("(?i)\\b" + Pattern.quote(original) + "\\b", w));
            if (knownPhrases.contains(candidatePhrase)) continue; // would also be a real idiom
            distractors.add(w);
        }
        if (distractors.size() < 3) return Optional.empty();
        List<String> cased = distractors.stream().map(d -> Phrases.matchCase(original, d)).toList();
        return Optional.of(assemble(position, idiom, QuestionType.FINISH, prompt, original, cased, rng));
    }

    /** Picks the FINISH distractor pool from the blank's context. */
    private List<String> distractorPool(Idiom idiom, String answer, List<String> content) {
        List<String> words = Phrases.words(idiom.headword());
        int at = words.indexOf(answer);
        if (at > 0 && DETERMINERS.contains(words.get(at - 1))) return nounWords;
        if (at == 0) return idiom.definition().startsWith("to ") ? verbWords : nounWords;
        return answer.equals(content.getLast()) ? lastWords : anyWords;
    }

    /** Puts "_____" into the original headword where the normalised one has it. */
    private static String restoreCase(String original, String normalizedWithBlank) {
        int at = normalizedWithBlank.indexOf(Question.BLANK);
        if (at < 0) return normalizedWithBlank;
        StringBuilder out = new StringBuilder();
        int o = 0;
        int n = 0;
        while (n < normalizedWithBlank.length()) {
            if (normalizedWithBlank.startsWith(Question.BLANK, n)) {
                out.append(Question.BLANK);
                n += Question.BLANK.length();
                while (o < original.length() && (Character.isLetter(original.charAt(o)) || original.charAt(o) == '\'' || original.charAt(o) == '’')) o++;
            } else {
                out.append(original.charAt(o));
                o++;
                n++;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ FITS

    Optional<Question> fits(int position, Idiom idiom, Set<Long> today, Random rng) {
        List<String> examples = new ArrayList<>(idiom.examples());
        Collections.shuffle(examples, rng);
        String rarest = Phrases.contentWords(idiom.headword()).stream()
                .min(Comparator.comparingInt(w -> wordFrequency.getOrDefault(w, 0)))
                .orElse(null);
        if (rarest == null) return Optional.empty();

        String sentence = null;
        for (String ex : examples) {
            Optional<String> blanked = blankIdiom(ex, idiom.headword());
            if (blanked.isPresent()
                    && Phrases.words(blanked.get()).size() >= 5  // enough context to reason from
                    && !wordPattern(rarest).matcher(Phrases.normalize(blanked.get())).find()) {
                sentence = blanked.get();
                break;
            }
        }
        if (sentence == null) return Optional.empty();

        Set<String> avoid = new HashSet<>(Phrases.contentWords(idiom.headword()));
        avoid.addAll(Phrases.contentWords(idiom.definition()));
        Set<String> sentenceWords = new HashSet<>(Phrases.words(sentence));
        boolean verbPhrase = idiom.definition().startsWith("to ");
        int size = Phrases.words(idiom.headword()).size();

        List<String> distractors = new ArrayList<>();
        Set<String> seen = new HashSet<>(Set.of(phraseKey(idiom.headword())));
        for (int t = 0; t < MAX_TRIES && distractors.size() < 3; t++) {
            Idiom other = pool.get(rng.nextInt(pool.size()));
            if (other.id() == idiom.id() || today.contains(other.id()) || other.funScore() < 45) continue;
            if (other.definition().startsWith("to ") != verbPhrase) continue;
            if (Math.abs(Phrases.words(other.headword()).size() - size) > 2) continue;
            List<String> otherWords = Phrases.contentWords(other.headword());
            if (otherWords.stream().anyMatch(w -> avoid.contains(w) || sentenceWords.contains(w))) continue;
            if (Phrases.contentWords(other.definition()).stream().anyMatch(avoid::contains)) continue;
            if (!seen.add(phraseKey(other.headword()))) continue;
            distractors.add(other.headword());
        }
        if (distractors.size() < 3) return Optional.empty();
        return Optional.of(assemble(position, idiom, QuestionType.FITS, sentence, idiom.headword(), distractors, rng));
    }

    /**
     * Finds the idiom inside an example sentence and replaces it with a blank. Placeholders match
     * a few words ("pat [Ann] on the back"), verbs may be inflected ("kicked", "bit"), optional
     * words may be absent ("(as) cool as a cucumber" ~ "cool as a cucumber").
     */
    static Optional<String> blankIdiom(String sentence, String headword) {
        Pattern p = idiomPattern(headword);
        if (p == null) return Optional.empty();
        String norm = Phrases.normalize(sentence);
        Matcher m = p.matcher(norm);
        if (!m.find()) return Optional.empty();
        StringBuilder out = new StringBuilder();
        int last = 0;
        do {
            out.append(sentence, last, m.start()).append(Question.BLANK);
            last = m.end();
        } while (m.find());
        out.append(sentence.substring(last));
        return Optional.of(out.toString());
    }

    static Pattern idiomPattern(String headword) {
        String h = Phrases.normalize(headword).replaceAll("[.!?]+$", "").strip()
                // one slot, not two: "root for someone or something"
                .replaceAll("\\b(someone|something)('s)? or (someone|something)('s)?\\b", "$1$2");
        // A trailing slot stays outside the blank: "Are you _____ me, or …" reads better than
        // swallowing unknown words. Same for trailing optional groups ("(with someone)").
        h = h.replaceAll("(\\s+\\([^)]*\\))+$", "")
             .replaceAll("\\s+(someone|something|someone's|something's|one|one's|somewhere)$", "");
        StringBuilder rx = new StringBuilder("(?<![\\w'])");
        boolean first = true;
        int literalTokens = 0;
        // Split into optional "(…)" groups and plain words.
        Matcher seg = Pattern.compile("\\(([^)]*)\\)|([^\\s()]+)").matcher(h);
        String sep = "[\\s,“”\"]+";
        while (seg.find()) {
            String token;
            if (seg.group(1) != null) {
                // Optional words: "(as)", "(cold)", "(with someone)"
                StringBuilder opt = new StringBuilder();
                for (String w : seg.group(1).trim().split("\\s+")) {
                    if (w.isEmpty()) continue;
                    opt.append(tokenRegex(w, false)).append(sep);
                }
                if (opt.isEmpty()) continue;
                if (first) {
                    rx.append("(?:").append(opt).append(")?");
                } else {
                    rx.append("(?:").append(sep).append(opt, 0, opt.length() - sep.length()).append(")?");
                }
                continue;
            }
            token = seg.group(2).replaceAll("[,;:]$", "");
            if (token.isEmpty()) continue;
            if (!first) rx.append(sep);
            rx.append(tokenRegex(token, first));
            if (!Phrases.PLACEHOLDERS.contains(token)) literalTokens++;
            first = false;
        }
        if (literalTokens < 2) return null; // too short to locate reliably
        rx.append("(?![\\w'])");
        return Pattern.compile(rx.toString());
    }

    private static String tokenRegex(String token, boolean first) {
        switch (token) {
            case "someone", "something", "somebody", "somewhere", "one" -> {
                return "(?:[\\w'’-]+\\s+){0,3}?[\\w'’-]+";
            }
            case "someone's", "one's", "something's" -> {
                return "[\\w'’-]+";
            }
            case "oneself" -> {
                return "\\w+sel(?:f|ves)";
            }
            default -> { }
        }
        String q = Pattern.quote(token);
        if (token.length() >= 3 && token.chars().allMatch(Character::isLetter)) {
            // Inflections: kick → kicks/kicked/kicking; bite → bit/bites/biting; first word may be
            // irregular (take → took), so it also matches on its first two letters.
            String stem = token.endsWith("e") ? token.substring(0, token.length() - 1) : token;
            String irregular = first ? "|" + Pattern.quote(token.substring(0, 2)) + "\\w{0,4}" : "";
            return "(?:" + q + "|" + Pattern.quote(stem) + "(?:s|es|ed|d|ing|" + stem.charAt(stem.length() - 1) + "ing|"
                    + stem.charAt(stem.length() - 1) + "ed)" + irregular + ")";
        }
        return q;
    }

    // ------------------------------------------------------------------ helpers

    private static Question assemble(int position, Idiom idiom, QuestionType type, String prompt,
                                     String correct, List<String> distractors, Random rng) {
        List<String> options = new ArrayList<>(distractors.subList(0, 3));
        int correctIndex = rng.nextInt(4);
        options.add(correctIndex, correct);
        return new Question(position, idiom.id(), type, prompt, options, correctIndex);
    }

    private static Pattern wordPattern(String word) {
        return Pattern.compile("(?<![\\w'])" + Pattern.quote(word) + "(?![\\w'])");
    }

    private static String phraseKey(String phrase) {
        return String.join(" ", Phrases.words(phrase));
    }

    Idiom idiom(long id) {
        return byId.get(id);
    }
}
