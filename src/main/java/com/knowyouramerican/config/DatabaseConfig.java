package com.knowyouramerican.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedded SQLite data source. Makes sure the DB file's directory exists, enables WAL so
 * readers don't block the puzzle scheduler, and turns on foreign keys on every connection.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

    @Bean
    DataSource dataSource(DatabaseProperties props) {
        Path db = Path.of(props.path()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(db.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create directory for SQLite DB at " + db, e);
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("sqlite");
        cfg.setJdbcUrl("jdbc:sqlite:" + db + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=true");
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(4);
        cfg.setConnectionInitSql("PRAGMA foreign_keys = ON");
        return new HikariDataSource(cfg);
    }
}
