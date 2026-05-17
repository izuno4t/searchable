package io.searchable.testkit.builder;

import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceFixturesTest {

    @Test
    void singleArgFactoryUsesIdAsNameAndDefaultConfig() {
        final Namespace ns = NamespaceFixtures.namespace("only-id");

        assertThat(ns.id()).isEqualTo("only-id");
        assertThat(ns.name()).isEqualTo("only-id");
        assertThat(ns.createdAt()).isEqualTo(NamespaceFixtures.DEFAULT_TIMESTAMP);
        assertThat(ns.updatedAt()).isEqualTo(NamespaceFixtures.DEFAULT_TIMESTAMP);
    }

    @Test
    void configOverloadAttachesProvidedConfig() {
        final NamespaceConfig custom = NamespaceConfig.defaults();
        final Namespace ns = NamespaceFixtures.namespace("with-cfg", custom);

        assertThat(ns.config()).isSameAs(custom);
        assertThat(ns.name()).isEqualTo("with-cfg");
    }

    @Test
    void builderSettersAllSetTheirField() {
        final Instant created = Instant.parse("2025-12-31T00:00:00Z");
        final Instant updated = Instant.parse("2026-01-15T00:00:00Z");
        final NamespaceConfig cfg = NamespaceConfig.defaults();

        final Namespace ns = NamespaceFixtures.builder()
            .id("custom-id")
            .name("Custom Name")
            .config(cfg)
            .createdAt(created)
            .updatedAt(updated)
            .build();

        assertThat(ns.id()).isEqualTo("custom-id");
        assertThat(ns.name()).isEqualTo("Custom Name");
        assertThat(ns.config()).isSameAs(cfg);
        assertThat(ns.createdAt()).isEqualTo(created);
        assertThat(ns.updatedAt()).isEqualTo(updated);
    }
}
