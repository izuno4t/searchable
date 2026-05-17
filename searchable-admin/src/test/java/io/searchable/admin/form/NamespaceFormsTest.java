package io.searchable.admin.form;

import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceFormsTest {

    @Test
    void createFormGettersAndSetters() {
        final NamespaceCreateForm form = new NamespaceCreateForm();
        form.setId("ns-x");
        form.setName("Display");
        form.setArchitecture(SearchType.HYBRID);
        form.setSearchStrategy(SearchStrategy.PARALLEL);
        form.setSearchOrder(SearchOrder.VECTOR_FIRST);

        assertThat(form.getId()).isEqualTo("ns-x");
        assertThat(form.getName()).isEqualTo("Display");
        assertThat(form.getArchitecture()).isEqualTo(SearchType.HYBRID);
        assertThat(form.getSearchStrategy()).isEqualTo(SearchStrategy.PARALLEL);
        assertThat(form.getSearchOrder()).isEqualTo(SearchOrder.VECTOR_FIRST);

        final var patch = form.toPatch();
        assertThat(patch.architecture()).isEqualTo(SearchType.HYBRID);
    }

    @Test
    void editFormGettersAndSetters() {
        final NamespaceEditForm form = new NamespaceEditForm();
        form.setName("Edit Me");
        form.setArchitecture(SearchType.VECTOR);
        form.setSearchStrategy(SearchStrategy.SEQUENTIAL);
        form.setSearchOrder(SearchOrder.FULL_TEXT_FIRST);

        assertThat(form.getName()).isEqualTo("Edit Me");
        assertThat(form.getArchitecture()).isEqualTo(SearchType.VECTOR);
        assertThat(form.getSearchStrategy()).isEqualTo(SearchStrategy.SEQUENTIAL);
        assertThat(form.getSearchOrder()).isEqualTo(SearchOrder.FULL_TEXT_FIRST);

        final var patch = form.toPatch();
        assertThat(patch.architecture()).isEqualTo(SearchType.VECTOR);
    }

    @Test
    void editFormFromNamespaceCopiesFields() {
        final Instant t = Instant.parse("2026-01-01T00:00:00Z");
        final Namespace ns = new Namespace("n", "Name", NamespaceConfig.defaults(), t, t);
        final NamespaceEditForm form = NamespaceEditForm.from(ns);

        assertThat(form.getName()).isEqualTo("Name");
        assertThat(form.getArchitecture()).isEqualTo(NamespaceConfig.defaults().architecture());
    }
}
