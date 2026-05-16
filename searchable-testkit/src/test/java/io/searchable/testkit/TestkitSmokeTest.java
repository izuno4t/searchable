package io.searchable.testkit;

import io.searchable.testkit.builder.DocumentFixtures;
import io.searchable.testkit.builder.NamespaceFixtures;
import io.searchable.testkit.builder.SearchRequestFixtures;
import io.searchable.testkit.db.H2DatabaseFixture;
import io.searchable.testkit.embedding.FakeEmbeddingProvider;
import io.searchable.testkit.lucene.LuceneIndexFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestkitSmokeTest {

    @TempDir Path tempDir;

    @Test
    void h2FixtureBootsAndInitializesSchema() {
        try (H2DatabaseFixture fx = H2DatabaseFixture.inMemory()) {
            assertThat(fx.dataSource()).isNotNull();
            assertThat(fx.config().type()).isEqualTo("H2");
            assertThat(fx.label()).isEqualTo("H2");
        }
    }

    @Test
    void luceneFixtureProvidesIndex() {
        try (LuceneIndexFixture fx = LuceneIndexFixture.create(tempDir)) {
            assertThat(fx.provider()).isNotNull();
            assertThat(fx.layout().rootDirectory()).isEqualTo(tempDir);
        }
    }

    @Test
    void namespaceFixturesProduceValidInstance() {
        assertThat(NamespaceFixtures.namespace("ns-1").id()).isEqualTo("ns-1");
        assertThat(NamespaceFixtures.builder().id("ns-2").name("N2").build().name())
            .isEqualTo("N2");
    }

    @Test
    void documentFixturesProvideDefaults() {
        assertThat(DocumentFixtures.document("d1").id()).isEqualTo("d1");
        assertThat(DocumentFixtures.builder("d2", "ns-x").build().namespaceId()).isEqualTo("ns-x");
    }

    @Test
    void searchRequestFixturesSetQueryAndNamespace() {
        final var req = SearchRequestFixtures.builder("hello", "ns-x").build();
        assertThat(req.query()).isEqualTo("hello");
        assertThat(req.namespaceIds()).containsExactly("ns-x");
    }

    @Test
    void fakeEmbeddingIsDeterministic() {
        try (FakeEmbeddingProvider p = new FakeEmbeddingProvider(64)) {
            assertThat(p.dimension()).isEqualTo(64);
            assertThat(p.embed("hello")).isEqualTo(p.embed("hello"));
            assertThat(p.identifier()).startsWith("fake:");
        }
    }
}
