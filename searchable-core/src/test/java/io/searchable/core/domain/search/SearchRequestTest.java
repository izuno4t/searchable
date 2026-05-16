package io.searchable.core.domain.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRequestTest {

    @Test
    void buildsWithDefaults() {
        final SearchRequest req = SearchRequest.builder().query("test").build();

        assertThat(req.query()).isEqualTo("test");
        assertThat(req.namespaceIds()).isEmpty();
        assertThat(req.searchType()).isNull();
        assertThat(req.options().highlightEnabled()).isTrue();
        assertThat(req.pagination().offset()).isZero();
        assertThat(req.pagination().limit()).isEqualTo(10);
    }

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> SearchRequest.builder().query("   ").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeOffset() {
        assertThatThrownBy(() -> new PaginationParams(-1, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroOrNegativeLimit() {
        assertThatThrownBy(() -> new PaginationParams(0, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsWithExplicitFields() {
        final SearchRequest req = SearchRequest.builder()
            .query("テスト")
            .namespaceIds(List.of("ns1", "ns2"))
            .searchType(SearchType.HYBRID)
            .pagination(new PaginationParams(10, 20))
            .build();

        assertThat(req.namespaceIds()).containsExactly("ns1", "ns2");
        assertThat(req.searchType()).isEqualTo(SearchType.HYBRID);
        assertThat(req.pagination().offset()).isEqualTo(10);
    }
}
