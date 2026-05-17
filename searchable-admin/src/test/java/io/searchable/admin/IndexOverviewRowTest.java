package io.searchable.admin;

import io.searchable.admin.controller.IndexViewController.IndexOverviewRow;
import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexStatus;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct ternary-branch coverage for {@link IndexOverviewRow}. */
class IndexOverviewRowTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private final Namespace ns = new Namespace("ns", "n", NamespaceConfig.defaults(), T0, T0);

    @Test
    void nullMetadataYieldsEmptyDefaults() {
        final IndexOverviewRow row = new IndexOverviewRow(ns, null);
        assertThat(row.status()).isEqualTo(IndexStatus.EMPTY);
        assertThat(row.documentCount()).isZero();
        assertThat(row.indexSizeBytes()).isZero();
    }

    @Test
    void presentMetadataPassesThroughGetters() {
        final IndexMetadata md = new IndexMetadata(
            "ns", 17L, 4096L, T0, IndexStatus.READY, Map.of());
        final IndexOverviewRow row = new IndexOverviewRow(ns, md);
        assertThat(row.status()).isEqualTo(IndexStatus.READY);
        assertThat(row.documentCount()).isEqualTo(17L);
        assertThat(row.indexSizeBytes()).isEqualTo(4096L);
    }
}
