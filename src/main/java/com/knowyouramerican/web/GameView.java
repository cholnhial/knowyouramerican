package com.knowyouramerican.web;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the game fragment renders for one day.
 *
 * @param results   per position: "correct" | "missed" | "pending"
 * @param card      the card on screen, or null when showing the summary
 * @param review    all five cards (answered ones revealed), for the summary
 * @param answers   the answers string to echo back on the next request
 */
public record GameView(
        LocalDate day,
        String dayLabel,
        boolean today,
        List<String> results,
        int done,
        int score,
        CardView card,
        int nextPosition,
        boolean finished,
        List<CardView> review,
        String shareText,
        String answers,
        LocalDate previousDay,
        LocalDate nextDay) {

    public String verdict() {
        return switch (score) {
            case 5 -> "Star-spangled! A perfect five.";
            case 4 -> "Sharp as a tack!";
            case 3 -> "Not too shabby.";
            case 2 -> "You win some, you lose some.";
            case 1 -> "Back to the drawing board.";
            default -> "Tough day — tomorrow's a new one.";
        };
    }

    public String eyebrow() {
        return today ? "Today's five" : "From the archive";
    }
}
