package com.knowyouramerican.idiom;

import java.time.LocalDate;
import java.util.List;

/**
 * One idiom from the seed, with its primary sense (the one questions are built from).
 *
 * @param usedOn the last date it appeared in a puzzle, or null if never
 */
public record Idiom(
        long id,
        String headword,
        List<String> altForms,
        String definition,
        String notes,
        List<String> examples,
        int funScore,
        LocalDate usedOn) {

    public Idiom withUsedOn(LocalDate date) {
        return new Idiom(id, headword, altForms, definition, notes, examples, funScore, date);
    }
}
