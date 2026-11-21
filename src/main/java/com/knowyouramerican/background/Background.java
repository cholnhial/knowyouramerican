package com.knowyouramerican.background;

/** One full-bleed background photo. The image lives at {@code /img/bg/{slug}.webp}. */
public record Background(
        String slug,
        String title,
        String location,
        String credit,
        String license,
        String sourceUrl) {

    public String imageUrl() {
        return "/img/bg/" + slug + ".webp";
    }
}
