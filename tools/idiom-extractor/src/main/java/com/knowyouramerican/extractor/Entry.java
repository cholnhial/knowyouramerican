package com.knowyouramerican.extractor;

import java.util.List;

/** One dictionary entry as parsed from the PDF. */
record Entry(
        String headword,
        List<String> altForms,
        List<Sense> senses,
        int page) {

    /**
     * One numbered sense. {@code forms} holds sense-specific phrasings
     * ("kick off ... 2. and kick the bucket to die"), which become idioms of their own.
     */
    record Sense(String definition, String notes, List<String> examples, List<String> forms) {}

    /** "Go to X." entries only point somewhere else. */
    boolean isCrossReferenceOnly() {
        return senses.isEmpty()
                || senses.stream().allMatch(s -> s.definition().startsWith("Go to ")
                                               || s.definition().startsWith("See "));
    }
}
