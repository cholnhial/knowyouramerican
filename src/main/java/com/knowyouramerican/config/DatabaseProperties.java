package com.knowyouramerican.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code kya.db.*} settings. */
@ConfigurationProperties("kya.db")
public record DatabaseProperties(String path) {

    public DatabaseProperties {
        if (path == null || path.isBlank()) {
            path = "./data/kya.db";
        }
    }
}
