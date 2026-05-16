package io.searchable.core.infrastructure.persistence;

import java.util.Objects;

/**
 * Configuration of the metadata database.
 *
 * @param type         persistence type identifier
 *                     ({@code H2}, {@code POSTGRESQL}, or generic {@code JDBC})
 * @param url          JDBC URL (e.g. {@code jdbc:h2:./data/metadata;MODE=PostgreSQL},
 *                     {@code jdbc:postgresql://host:5432/db})
 * @param username     database username
 * @param password     database password (may be empty for H2 embedded)
 * @param maxPoolSize  maximum number of pooled connections (defaults to {@value #DEFAULT_POOL_SIZE}
 *                     when non-positive)
 */
public record PersistenceConfig(
    String type,
    String url,
    String username,
    String password,
    int maxPoolSize
) {

    public static final int DEFAULT_POOL_SIZE = 16;

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
        if (maxPoolSize <= 0) {
            maxPoolSize = DEFAULT_POOL_SIZE;
        }
    }

    /**
     * Convenience constructor that omits {@code maxPoolSize}; the default
     * ({@value #DEFAULT_POOL_SIZE}) is applied.
     */
    public PersistenceConfig(final String type, final String url,
                             final String username, final String password) {
        this(type, url, username, password, DEFAULT_POOL_SIZE);
    }
}
