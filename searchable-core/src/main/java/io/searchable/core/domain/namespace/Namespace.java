package io.searchable.core.domain.namespace;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A logical isolation unit for indexed documents.
 *
 * <p>Namespace IDs follow the same rules as URL-safe identifiers:
 * lowercase letters, digits, hyphens, and underscores only.
 */
public final class Namespace {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private final String id;
    private final String name;
    private final NamespaceConfig config;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Namespace(final String id,
                     final String name,
                     final NamespaceConfig config,
                     final Instant createdAt,
                     final Instant updatedAt) {
        this.id = validateId(id);
        this.name = Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static String validateId(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                "id must match " + ID_PATTERN.pattern() + ", was: " + id);
        }
        return id;
    }

    public String id() { return id; }
    public String name() { return name; }
    public NamespaceConfig config() { return config; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public Namespace withConfig(final NamespaceConfig newConfig, final Instant now) {
        return new Namespace(id, name, newConfig, createdAt, now);
    }

    public Namespace withName(final String newName, final Instant now) {
        return new Namespace(id, newName, config, createdAt, now);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Namespace that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Namespace{id=" + id + ", name=" + name + "}";
    }
}
