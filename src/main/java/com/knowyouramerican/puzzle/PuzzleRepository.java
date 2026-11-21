package com.knowyouramerican.puzzle;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class PuzzleRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public PuzzleRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public boolean exists(LocalDate day) {
        return jdbc.sql("SELECT count(*) FROM puzzle_day WHERE day_date = ?")
                .param(day.toString()).query(Integer.class).single() > 0;
    }

    public Set<LocalDate> existingBetween(LocalDate from, LocalDate to) {
        return new HashSet<>(jdbc.sql("SELECT day_date FROM puzzle_day WHERE day_date BETWEEN ? AND ?")
                .param(from.toString()).param(to.toString())
                .query((rs, n) -> LocalDate.parse(rs.getString(1)))
                .list());
    }

    public int count() {
        return jdbc.sql("SELECT count(*) FROM puzzle_day").query(Integer.class).single();
    }

    public void save(LocalDate day, List<Question> questions) {
        jdbc.sql("INSERT INTO puzzle_day (day_date) VALUES (?)").param(day.toString()).update();
        for (Question q : questions) {
            jdbc.sql("""
                            INSERT INTO question (day_date, position, idiom_id, type, prompt, options_json, correct_index)
                            VALUES (?, ?, ?, ?, ?, ?, ?)""")
                    .param(day.toString())
                    .param(q.position())
                    .param(q.idiomId())
                    .param(q.type().name())
                    .param(q.prompt())
                    .param(json.writeValueAsString(q.options()))
                    .param(q.correctIndex())
                    .update();
        }
    }

    /** The day's questions in position order, or empty if the day hasn't been generated. */
    public List<Question> findQuestions(LocalDate day) {
        return jdbc.sql("""
                        SELECT position, idiom_id, type, prompt, options_json, correct_index
                        FROM question WHERE day_date = ? ORDER BY position""")
                .param(day.toString())
                .query((rs, n) -> new Question(
                        rs.getInt("position"),
                        rs.getLong("idiom_id"),
                        QuestionType.valueOf(rs.getString("type")),
                        rs.getString("prompt"),
                        json.readValue(rs.getString("options_json"), STRINGS),
                        rs.getInt("correct_index")))
                .list();
    }

    public Optional<Question> findQuestion(LocalDate day, int position) {
        return findQuestions(day).stream().filter(q -> q.position() == position).findFirst();
    }
}
