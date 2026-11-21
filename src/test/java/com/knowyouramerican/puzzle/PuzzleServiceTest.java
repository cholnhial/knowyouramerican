package com.knowyouramerican.puzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowyouramerican.support.TempDatabase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class PuzzleServiceTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        TempDatabase.register(registry);
    }

    @Autowired PuzzleService service;
    @Autowired PuzzleRepository puzzles;
    @Autowired JdbcClient jdbc;

    @Test
    void backfillCreatesEveryDayOnceAndIsIdempotent() {
        LocalDate last = PuzzleCalendar.FIRST_DAY.plusDays(29);

        assertThat(service.ensureThrough(last)).isEqualTo(30);
        assertThat(puzzles.count()).isEqualTo(30);
        assertThat(service.ensureThrough(last)).isZero();   // rerun: nothing new
        assertThat(service.ensureThrough(last.plusDays(1))).isEqualTo(1);

        // Stored puzzles read back intact
        assertThat(puzzles.findQuestions(PuzzleCalendar.FIRST_DAY))
                .hasSize(5)
                .extracting(Question::position).containsExactly(1, 2, 3, 4, 5);

        // 31 days × 5 idioms, each marked with the day it was used
        assertThat(jdbc.sql("SELECT count(*) FROM idiom WHERE used_on IS NOT NULL")
                .query(Integer.class).single()).isEqualTo(155);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM question q JOIN idiom i ON i.id = q.idiom_id
                        WHERE i.used_on <> q.day_date""")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void nothingBeforeFirstDay() {
        assertThat(service.ensureThrough(PuzzleCalendar.FIRST_DAY.minusDays(1))).isZero();
    }
}
