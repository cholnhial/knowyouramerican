package com.knowyouramerican.puzzle;

import java.util.List;

/**
 * One generated question.
 *
 * @param prompt  MEANING: the idiom; FINISH: the idiom with "___"; FITS: a sentence with "_____"
 * @param options exactly four, one of them correct
 */
public record Question(
        int position,
        long idiomId,
        QuestionType type,
        String prompt,
        List<String> options,
        int correctIndex) {

    public static final String BLANK = "_____";

    public Question {
        options = List.copyOf(options);
        if (options.size() != 4) throw new IllegalArgumentException("need 4 options, got " + options);
        if (correctIndex < 0 || correctIndex > 3) throw new IllegalArgumentException("bad correct index");
    }
}
