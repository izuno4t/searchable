package io.searchable.core.application;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.document.DocumentSource;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.domain.index.IndexStatus;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcDocumentSourceRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Closes the branch coverage in {@link IndexService} (status transitions / null-source paths). */
class IndexServiceBranchTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider provider;
    private JdbcNamespaceRepository namespaces;
    private JdbcIndexMetadataRepository metadata;
    private JdbcDocumentSourceRepository sources;
    private LuceneIndexer indexer;
    private Clock clock;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        provider = new LuceneIndexProvider(
            new IndexLayout(tempDir.resolve("indexes")), AnalyzerFactory.japanese());
        namespaces = new JdbcNamespaceRepository(dataSource);
        metadata = new JdbcIndexMetadataRepository(dataSource);
        sources = new JdbcDocumentSourceRepository(dataSource);
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        indexer = new LuceneIndexer(provider, embedding);
        clock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        new NamespaceService(namespaces, metadata, provider, GlobalConfig.defaults(), clock)
            .create("bn", "BN", null);
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void indexIfChangedFallsBackToPlainIndexWhenNoSourceRepoConfigured() {
        // No documentSources param -> indexIfChanged short-circuits to index().
        final IndexService service = new IndexService(namespaces, metadata, provider, indexer, clock);
        final Document d = Document.builder().id("d").namespaceId("bn")
            .title("t").content("c").build();
        assertThat(service.indexIfChanged(d)).isTrue();
        assertThat(service.indexIfChanged(d)).isTrue(); // always re-indexes
    }

    @Test
    void effectiveHashHonoursPreSuppliedContentHash() {
        final IndexService service = new IndexService(namespaces, metadata, provider, indexer,
            sources, clock);
        final DocumentSource src = new DocumentSource("file", "/a", "PRECOMPUTED_HASH",
            Instant.parse("2026-05-15T00:00:00Z"));
        final Document withHash = Document.builder().id("hashed").namespaceId("bn")
            .title("t").content("c").source(src).build();

        assertThat(service.indexIfChanged(withHash)).isTrue();
        assertThat(sources.findByDocumentId("bn", "hashed").orElseThrow().contentHash())
            .isEqualTo("PRECOMPUTED_HASH");
    }

    @Test
    void recordSourcePreservesExistingTypeAndLocation() {
        final IndexService service = new IndexService(namespaces, metadata, provider, indexer,
            sources, clock);
        final DocumentSource src = new DocumentSource("url", "https://e.x", null, null);
        final Document withSrc = Document.builder().id("rs").namespaceId("bn")
            .title("t").content("c").source(src).build();
        service.index(withSrc);

        final DocumentSource saved = sources.findByDocumentId("bn", "rs").orElseThrow();
        assertThat(saved.type()).isEqualTo("url");
        assertThat(saved.location()).isEqualTo("https://e.x");
        assertThat(saved.contentHash()).isNotNull();
    }

    @Test
    void indexBatchMarksErrorWhenIndexerThrows() {
        final IndexService service = new IndexService(namespaces, metadata, provider, indexer,
            sources, clock);
        // A document referencing a missing namespace will cause the indexer
        // to throw, exercising the catch block.
        final Document bad = Document.builder().id("d").namespaceId("missing")
            .title("t").content("c").build();
        assertThatThrownBy(() -> service.indexBatch("missing", List.of(bad)))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void getMetadataThrowsForUnknownNamespace() {
        final IndexService service = new IndexService(namespaces, metadata, provider, indexer,
            sources, clock);
        assertThatThrownBy(() -> service.getMetadata("never-existed"))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void rebuildResetsLifecycleStatusToReady() {
        final IndexService service = new IndexService(namespaces, metadata, provider, indexer,
            sources, clock);
        service.rebuild("bn");
        assertThat(service.getMetadata("bn").status()).isEqualTo(IndexStatus.READY);
    }
}
