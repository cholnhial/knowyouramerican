package com.knowyouramerican.support;

import com.knowyouramerican.idiom.Idiom;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The real seed as {@link Idiom}s (ids = line numbers), for fast tests without a database. */
public final class SeedPool {

    private static List<Idiom> cached;

    private SeedPool() {}

    public static synchronized List<Idiom> load() {
        if (cached != null) return cached;
        JsonMapper json = JsonMapper.builder().build();
        TypeReference<List<String>> strings = new TypeReference<>() {};
        List<Idiom> out = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("db/seed/idioms.jsonl").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            long id = 1;
            while ((line = reader.readLine()) != null) {
                JsonNode n = json.readTree(line);
                JsonNode primary = n.get("senses").get(0);
                for (JsonNode s : n.get("senses")) {
                    if (!s.get("examples").isEmpty()) { primary = s; break; }
                }
                out.add(new Idiom(id++, n.get("headword").asString(),
                        json.convertValue(n.get("altForms"), strings),
                        primary.get("definition").asString(),
                        primary.get("notes").isNull() ? null : primary.get("notes").asString(),
                        json.convertValue(primary.get("examples"), strings),
                        n.get("funScore").asInt(), null));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cached = List.copyOf(out);
        return cached;
    }
}
