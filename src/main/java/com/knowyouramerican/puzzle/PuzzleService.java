package com.knowyouramerican.puzzle;

import com.knowyouramerican.idiom.Idiom;
import com.knowyouramerican.idiom.IdiomRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Makes sure every playable day has a stored puzzle. Safe to call any number of times. */
@Service
public class PuzzleService {

    private static final Logger log = LoggerFactory.getLogger(PuzzleService.class);

    private final PuzzleRepository puzzles;
    private final IdiomRepository idioms;
    private final TransactionTemplate tx;

    public PuzzleService(PuzzleRepository puzzles, IdiomRepository idioms, TransactionTemplate tx) {
        this.puzzles = puzzles;
        this.idioms = idioms;
        this.tx = tx;
    }

    /**
     * Generates any missing days from {@link PuzzleCalendar#FIRST_DAY} through {@code last},
     * oldest first (so idiom rotation follows the calendar). Each day commits on its own.
     *
     * @return how many days were created
     */
    public synchronized int ensureThrough(LocalDate last) {
        LocalDate first = PuzzleCalendar.FIRST_DAY;
        if (last.isBefore(first)) return 0;
        Set<LocalDate> existing = puzzles.existingBetween(first, last);
        long span = first.datesUntil(last.plusDays(1)).count();
        if (existing.size() == span) return 0;

        List<Idiom> pool = idioms.findAll();
        PuzzleGenerator generator = new PuzzleGenerator(pool);
        Map<Long, LocalDate> usage = new HashMap<>();
        pool.forEach(i -> { if (i.usedOn() != null) usage.put(i.id(), i.usedOn()); });

        int created = 0;
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (existing.contains(day)) continue;
            List<Question> questions = generator.generate(day, usage);
            LocalDate d = day;
            tx.executeWithoutResult(status -> {
                puzzles.save(d, questions);
                idioms.markUsed(questions.stream().map(Question::idiomId).toList(), d);
            });
            questions.forEach(q -> usage.merge(q.idiomId(), d, (a, b) -> a.isAfter(b) ? a : b));
            created++;
        }
        log.info("Generated {} puzzle day(s) through {}", created, last);
        return created;
    }
}
