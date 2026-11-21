package com.knowyouramerican.background;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Loads {@code backgrounds.json} once and hands out a random background per page load. */
@Component
@RegisterReflectionForBinding(Background.class)
@ImportRuntimeHints(BackgroundCatalog.Hints.class)
public class BackgroundCatalog {

    static final String CATALOG = "backgrounds.json";

    private final List<Background> backgrounds;

    public BackgroundCatalog(JsonMapper json) {
        try (InputStream in = new ClassPathResource(CATALOG).getInputStream()) {
            this.backgrounds = List.copyOf(json.readValue(in, new TypeReference<List<Background>>() {}));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read backgrounds.json", e);
        }
        if (backgrounds.isEmpty()) {
            throw new IllegalStateException("backgrounds.json must list at least one background");
        }
    }

    public Background random() {
        return backgrounds.get(ThreadLocalRandom.current().nextInt(backgrounds.size()));
    }

    public List<Background> all() {
        return backgrounds;
    }

    /** Native image: include the catalog file (static/** is bundled by Spring already). */
    static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern(CATALOG);
        }
    }
}
