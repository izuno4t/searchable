package io.searchable.core.application;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.document.ContentHashes;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.document.DocumentSource;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.domain.document.DocumentMetadataRecord;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcDocumentMetadataRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

class IndexServiceContentHashTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider provider;
    private IndexService service;
    private NamespaceService namespaceService;
    private JdbcDocumentMetadataRepository metadataRepo;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        provider = new LuceneIndexProvider(
            new IndexLayout(tempDir.resolve("indexes")), AnalyzerFactory.japanese());
        final var namespaces = new JdbcNamespaceRepository(dataSource);
        final var metadata = new JdbcIndexMetadataRepository(dataSource);
        metadataRepo = new JdbcDocumentMetadataRepository(dataSource);
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        final LuceneIndexer indexer = new LuceneIndexer(provider, embedding);
        final Clock clock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        service = new IndexService(namespaces, metadata, provider, indexer, metadataRepo, clock);
        namespaceService = new NamespaceService(namespaces, metadata, provider,
            GlobalConfig.defaults(), clock);
        namespaceService.create("ch_ns", "CH", null);
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void firstIngestStoresHashAndIndexesDocument() {
        final Document doc = doc("doc-1", "title", "本文1");
        assertThat(service.indexIfChanged(doc)).isTrue();
        assertThat(metadataRepo.findById("ch_ns", "doc-1"))
            .isPresent()
            .get()
            .extracting(DocumentMetadataRecord::source)
            .extracting(DocumentSource::contentHash)
            .isEqualTo(ContentHashes.hash(doc));
    }

    @Test
    void secondIngestWithSameContentIsSkipped() {
        final Document doc = doc("doc-2", "title", "本文2");
        assertThat(service.indexIfChanged(doc)).isTrue();
        assertThat(service.indexIfChanged(doc)).isFalse();
    }

    @Test
    void changedContentTriggersReIndex() {
        final Document v1 = doc("doc-3", "title", "v1");
        final Document v2 = doc("doc-3", "title", "v2");
        assertThat(service.indexIfChanged(v1)).isTrue();
        assertThat(service.indexIfChanged(v2)).isTrue();
        assertThat(metadataRepo.findById("ch_ns", "doc-3").orElseThrow().source().contentHash())
            .isEqualTo(ContentHashes.hash(v2));
    }

    private Document doc(final String id, final String title, final String content) {
        return Document.builder()
            .id(id)
            .namespaceId("ch_ns")
            .title(title)
            .content(content)
            .build();
    }
}
