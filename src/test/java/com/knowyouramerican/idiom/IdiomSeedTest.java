package com.knowyouramerican.idiom;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowyouramerican.support.TempDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class IdiomSeedTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        TempDatabase.register(registry);
    }

    @Autowired
    JdbcClient jdbc;

    @Test
    void seedMigrationRanAsVersion3() {
        String desc = jdbc.sql("SELECT description FROM flyway_schema_history WHERE version = '3'")
                .query(String.class).single();
        assertThat(desc).isEqualTo("seed idioms");
    }

    @Test
    void poolIsLargeAndComplete() {
        assertThat(count("SELECT count(*) FROM idiom")).isGreaterThan(5000);
        assertThat(count("SELECT count(*) FROM idiom WHERE trim(definition) = ''")).isZero();
        assertThat(count("SELECT count(*) FROM idiom WHERE fun_score NOT BETWEEN 0 AND 100")).isZero();
        assertThat(count("SELECT count(*) FROM idiom WHERE json_array_length(senses_json) = 0")).isZero();
        // Nearly every idiom can be used for "Which idiom fits?"
        assertThat(count("SELECT count(*) FROM idiom WHERE json_array_length(examples_json) > 0"))
                .isGreaterThan(count("SELECT count(*) FROM idiom") * 95 / 100);
    }

    @Test
    void knownIdiomLoadedWithPrimarySense() {
        var row = jdbc.sql("""
                        SELECT definition, notes, json_array_length(examples_json) AS ex, fun_score
                        FROM idiom WHERE headword = 'bite the bullet'""")
                .query((rs, n) -> new Object[] {
                        rs.getString("definition"), rs.getString("notes"), rs.getInt("ex"), rs.getInt("fun_score")})
                .single();
        assertThat(row[0]).isEqualTo("to put up with or endure (something).");
        assertThat(row[1]).isEqualTo("Informal or slang.");
        assertThat((int) row[2]).isEqualTo(2);
        assertThat((int) row[3]).isGreaterThan(80);
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
