package com.knowyouramerican;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowyouramerican.support.TempDatabase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class DatabaseSchemaTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        TempDatabase.register(registry);
    }

    @Autowired
    JdbcClient jdbc;

    @Test
    void flywayCreatesCoreTables() {
        List<String> tables = jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
                .query(String.class)
                .list();
        assertThat(tables).contains("idiom", "puzzle_day", "question");
    }

    @Test
    void foreignKeysAndWalAreOn() {
        assertThat(jdbc.sql("PRAGMA foreign_keys").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualToIgnoringCase("wal");
    }
}
