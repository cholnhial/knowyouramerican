package com.knowyouramerican.background;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Loads {@code backgrounds.json} once and hands out a random background per page load. */
@Component
@RegisterReflectionForBinding(Background.class)
public class BackgroundCatalog {

    private final List<Background> backgrounds;

    public BackgroundCatalog(JsonMapper json) {
        try (InputStream in = new ClassPathResource("backgrounds.json").getInputStream()) {
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
}
