package com.knowyouramerican.web;

import com.knowyouramerican.puzzle.QuestionType;
import java.util.List;

/**
 * One question card as the template sees it. Until the card is answered, {@code correctIndex} is
 * -1 and the reveal fields are null, so the answer never reaches the page early.
 *
 * @param promptParts the prompt split at each blank (FINISH/FITS have ≥ 2 parts; MEANING has 1)
 * @param justAnswered this response is the answer to a click (play the shake/pop animation)
 */
public record CardView(
        int position,
        QuestionType type,
        String typeLabel,
        String instruction,
        List<String> promptParts,
        String hint,
        List<String> options,
        boolean answered,
        int chosen,
        int correctIndex,
        boolean justAnswered,
        Reveal reveal) {

    /** What the player learns after answering. */
    public record Reveal(String headword, String meaning, String example, String notes) {}

    /** Enum name as a plain string, for th:switch (no enum reflection in native images). */
    public String typeName() {
        return type.name();
    }

    public boolean hasBlank() {
        return promptParts.size() >= 2;
    }

    public boolean correct() {
        return answered && chosen == correctIndex;
    }

    /** Option letter A–D. */
    public String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    /** Answer shown in the blank once answered (the right one). */
    public String blankFill() {
        return answered ? options.get(correctIndex) : null;
    }

    /** CSS state of an option button: idle | right | wrong | faded. */
    public String optionState(int index) {
        if (!answered) return "idle";
        if (index == correctIndex) return "right";
        if (index == chosen) return "wrong";
        return "faded";
    }
}
