package com.knowyouramerican.idiom;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class IdiomRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public IdiomRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** The whole pool (~6k rows), in id order so generation is deterministic. */
    public List<Idiom> findAll() {
        return jdbc.sql("""
                        SELECT id, headword, alt_forms, definition, notes, examples_json, fun_score, used_on
                        FROM idiom ORDER BY id""")
                .query((rs, n) -> new Idiom(
                        rs.getLong("id"),
                        rs.getString("headword"),
                        strings(rs.getString("alt_forms")),
                        rs.getString("definition"),
                        rs.getString("notes"),
                        strings(rs.getString("examples_json")),
                        rs.getInt("fun_score"),
                        Optional.ofNullable(rs.getString("used_on")).map(LocalDate::parse).orElse(null)))
                .list();
    }

    public Optional<Idiom> findById(long id) {
        return jdbc.sql("""
                        SELECT id, headword, alt_forms, definition, notes, examples_json, fun_score, used_on
                        FROM idiom WHERE id = ?""")
                .param(id)
                .query((rs, n) -> new Idiom(
                        rs.getLong("id"),
                        rs.getString("headword"),
                        strings(rs.getString("alt_forms")),
                        rs.getString("definition"),
                        rs.getString("notes"),
                        strings(rs.getString("examples_json")),
                        rs.getInt("fun_score"),
                        Optional.ofNullable(rs.getString("used_on")).map(LocalDate::parse).orElse(null)))
                .optional();
    }

    public void markUsed(Collection<Long> ids, LocalDate date) {
        for (long id : ids) {
            jdbc.sql("UPDATE idiom SET used_on = ? WHERE id = ?").param(date.toString()).param(id).update();
        }
    }

    private List<String> strings(String raw) {
        return raw == null || raw.isBlank() ? List.of() : json.readValue(raw, STRINGS);
    }
}
