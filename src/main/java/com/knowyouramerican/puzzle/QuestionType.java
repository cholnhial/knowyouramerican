package com.knowyouramerican.puzzle;

/** The three ways an idiom is asked. */
public enum QuestionType {
    /** Show the idiom, pick its meaning. */
    MEANING("What does it mean?"),
    /** The idiom with one word blanked, pick the missing word. The meaning is shown as a hint. */
    FINISH("Finish the idiom"),
    /** An example sentence with the idiom blanked, pick the idiom. */
    FITS("Which idiom fits?");

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
