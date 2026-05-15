package com.searchable.core.infrastructure.persistence;

import java.util.Objects;

/**
 * Configuration of the metadata database.
 *
 * @param type     persistence type identifier (e.g. {@code H2})
 * @param url      JDBC URL (e.g. {@code jdbc:h2:./data/metadata;MODE=PostgreSQL})
 * @param username database username
 * @param password database password (may be empty for H2 embedded)
 */
public record PersistenceConfig(String type, String url, String username, String password) {

    public PersistenceConfig {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(username, "username must not be null");
        password = password == null ? "" : password;
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
    }
}
