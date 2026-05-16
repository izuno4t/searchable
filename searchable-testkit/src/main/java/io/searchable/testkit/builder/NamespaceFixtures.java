package io.searchable.testkit.builder;

import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;

import java.time.Instant;

/** Convenience factories for {@link Namespace} instances in tests. */
public final class NamespaceFixtures {

    /** Default timestamp used by {@link #namespace(String)}. */
    public static final Instant DEFAULT_TIMESTAMP = Instant.parse("2026-01-01T00:00:00Z");

    private NamespaceFixtures() { }

    /** Namespace with the given id, default name, and default config. */
    public static Namespace namespace(final String id) {
        return namespace(id, id);
    }

    public static Namespace namespace(final String id, final String name) {
        return new Namespace(id, name, NamespaceConfig.defaults(),
            DEFAULT_TIMESTAMP, DEFAULT_TIMESTAMP);
    }

    public static Namespace namespace(final String id, final NamespaceConfig config) {
        return new Namespace(id, id, config, DEFAULT_TIMESTAMP, DEFAULT_TIMESTAMP);
    }

    public static Builder builder() { return new Builder(); }

    /** Mutable builder for tests that need non-default fields. */
    public static final class Builder {
        private String id = "test-ns";
        private String name = "Test Namespace";
        private NamespaceConfig config = NamespaceConfig.defaults();
        private Instant createdAt = DEFAULT_TIMESTAMP;
        private Instant updatedAt = DEFAULT_TIMESTAMP;

        public Builder id(final String v) { this.id = v; return this; }
        public Builder name(final String v) { this.name = v; return this; }
        public Builder config(final NamespaceConfig v) { this.config = v; return this; }
        public Builder createdAt(final Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(final Instant v) { this.updatedAt = v; return this; }

        public Namespace build() {
            return new Namespace(id, name, config, createdAt, updatedAt);
        }
    }
}
