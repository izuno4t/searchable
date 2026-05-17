package io.searchable.core.domain.index;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexMetadataExtraTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejectsNullNamespaceOrStatus() {
        assertThatThrownBy(() ->
            new IndexMetadata(null, 0, 0, T0, IndexStatus.READY, Map.of()))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
            new IndexMetadata("ns", 0, 0, T0, null, Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void statisticsAreDefensivelyCopied() {
        final Map<String, Object> s = new HashMap<>();
        s.put("k", "v");
        final IndexMetadata m = new IndexMetadata("ns", 1, 1, T0, IndexStatus.READY, s);
        s.put("k", "MUT");
        s.put("k2", "v2");
        assertThat(m.statistics()).containsOnlyKeys("k");
    }
}
