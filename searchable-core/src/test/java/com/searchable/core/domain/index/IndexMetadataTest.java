package com.searchable.core.domain.index;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexMetadataTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void emptyMetadataStartsAtZero() {
        final IndexMetadata m = IndexMetadata.empty("ns1", T0);
        assertThat(m.documentCount()).isZero();
        assertThat(m.indexSizeBytes()).isZero();
        assertThat(m.status()).isEqualTo(IndexStatus.EMPTY);
        assertThat(m.lastUpdated()).isEqualTo(T0);
    }

    @Test
    void rejectsNegativeDocumentCount() {
        assertThatThrownBy(() ->
            new IndexMetadata("ns", -1L, 0L, T0, IndexStatus.READY, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeIndexSize() {
        assertThatThrownBy(() ->
            new IndexMetadata("ns", 0L, -1L, T0, IndexStatus.READY, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
