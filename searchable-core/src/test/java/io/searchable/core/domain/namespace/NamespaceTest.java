package io.searchable.core.domain.namespace;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    void buildsWithValidArguments() {
        final Namespace ns = new Namespace("project-a", "Project A",
            NamespaceConfig.defaults(), T0, T0);

        assertThat(ns.id()).isEqualTo("project-a");
        assertThat(ns.name()).isEqualTo("Project A");
        assertThat(ns.createdAt()).isEqualTo(T0);
        assertThat(ns.updatedAt()).isEqualTo(T0);
    }

    @Test
    void rejectsInvalidIdPattern() {
        assertThatThrownBy(() -> new Namespace("Invalid/ID", "n",
            NamespaceConfig.defaults(), T0, T0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must match");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new Namespace("ns1", "  ",
            NamespaceConfig.defaults(), T0, T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUpdatedBeforeCreated() {
        assertThatThrownBy(() -> new Namespace("ns1", "n",
            NamespaceConfig.defaults(), T1, T0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("updatedAt");
    }

    @Test
    void equalityIsBasedOnId() {
        final Namespace a = new Namespace("ns1", "A", NamespaceConfig.defaults(), T0, T0);
        final Namespace b = new Namespace("ns1", "B (different)",
            NamespaceConfig.defaults(), T0, T1);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void withConfigPreservesIdAndCreatedAt() {
        final Namespace ns = new Namespace("ns1", "n", NamespaceConfig.defaults(), T0, T0);
        final NamespaceConfig newConfig = new NamespaceConfig(
            io.searchable.core.domain.search.SearchType.HYBRID,
            io.searchable.core.domain.search.SearchStrategy.PARALLEL,
            io.searchable.core.domain.search.SearchOrder.FULL_TEXT_FIRST,
            null, AiConfig.disabled(), null);

        final Namespace updated = ns.withConfig(newConfig, T1);
        assertThat(updated.id()).isEqualTo("ns1");
        assertThat(updated.createdAt()).isEqualTo(T0);
        assertThat(updated.updatedAt()).isEqualTo(T1);
        assertThat(updated.config().architecture())
            .isEqualTo(io.searchable.core.domain.search.SearchType.HYBRID);
    }
}
