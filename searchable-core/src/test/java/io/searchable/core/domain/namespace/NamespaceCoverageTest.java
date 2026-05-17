package io.searchable.core.domain.namespace;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceCoverageTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejectsNullOrInvalidId() {
        assertThatThrownBy(() ->
            new Namespace(null, "n", NamespaceConfig.defaults(), T0, T0))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            new Namespace("Bad ID", "n", NamespaceConfig.defaults(), T0, T0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() ->
            new Namespace("n", " ", NamespaceConfig.defaults(), T0, T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUpdatedBeforeCreated() {
        assertThatThrownBy(() -> new Namespace("n", "Name", NamespaceConfig.defaults(),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("updatedAt");
    }

    @Test
    void rejectsNullTimestampsOrConfig() {
        assertThatThrownBy(() -> new Namespace("n", "x", null, T0, T0))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Namespace("n", "x", NamespaceConfig.defaults(), null, T0))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Namespace("n", "x", NamespaceConfig.defaults(), T0, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withConfigAndWithNameProduceNewInstancesAndPreserveCreated() {
        final Namespace ns = new Namespace("n", "Original", NamespaceConfig.defaults(), T0, T0);
        final Instant later = T0.plusSeconds(60);

        final Namespace renamed = ns.withName("Renamed", later);
        assertThat(renamed.name()).isEqualTo("Renamed");
        assertThat(renamed.createdAt()).isEqualTo(T0);
        assertThat(renamed.updatedAt()).isEqualTo(later);

        final Namespace reconfigured = ns.withConfig(NamespaceConfig.defaults(), later);
        assertThat(reconfigured.updatedAt()).isEqualTo(later);
        assertThat(reconfigured.id()).isEqualTo(ns.id());
    }

    @Test
    void equalsAndHashCodeUseIdOnly() {
        final Namespace a = new Namespace("same", "A", NamespaceConfig.defaults(), T0, T0);
        final Namespace b = new Namespace("same", "B", NamespaceConfig.defaults(), T0, T0);
        final Namespace other = new Namespace("diff", "A", NamespaceConfig.defaults(), T0, T0);

        assertThat(a).isEqualTo(a).isEqualTo(b).isNotEqualTo(other).isNotEqualTo("string").isNotEqualTo(null);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringContainsIdAndName() {
        final Namespace ns = new Namespace("n", "Display", NamespaceConfig.defaults(), T0, T0);
        assertThat(ns.toString()).contains("n").contains("Display");
    }
}
