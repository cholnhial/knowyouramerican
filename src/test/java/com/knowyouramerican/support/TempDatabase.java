package com.knowyouramerican.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Points {@code kya.db.path} at a fresh SQLite file for a test class, and turns off the puzzle
 * scheduler so tests decide which days exist.
 */
public final class TempDatabase {

    private TempDatabase() {}

    public static void register(DynamicPropertyRegistry registry) {
        try {
            Path dir = Files.createTempDirectory("kya-test-");
            registry.add("kya.db.path", () -> dir.resolve("kya.db").toString());
            registry.add("kya.puzzles.scheduler-enabled", () -> "false");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
