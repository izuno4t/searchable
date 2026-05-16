package io.searchable.core.application;

import io.searchable.core.application.config.GlobalConfig;
import io.searchable.core.domain.document.Document;
import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexStatus;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.LuceneIndexer;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
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
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexServiceTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider indexProvider;
    private NamespaceService namespaceService;
    private IndexService indexService;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        indexProvider = new LuceneIndexProvider(
            new IndexLayout(tempDir.resolve("indexes")), AnalyzerFactory.japanese());

        final JdbcNamespaceRepository namespaces = new JdbcNamespaceRepository(dataSource);
        final JdbcIndexMetadataRepository md = new JdbcIndexMetadataRepository(dataSource);
        final Clock fixed = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        namespaceService = new NamespaceService(namespaces, md, indexProvider,
            GlobalConfig.defaults(), fixed);
        indexService = new IndexService(namespaces, md, indexProvider,
            new LuceneIndexer(indexProvider), fixed);
        namespaceService.create("ns-1", "n", null);
    }

    @AfterEach
    void tearDown() throws Exception {
        indexProvider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    private Document doc(final String id, final String title, final String content) {
        return Document.builder().id(id).namespaceId("ns-1")
            .title(title).content(content).build();
    }

    @Test
    void indexUpdatesMetadata() {
        indexService.index(doc("d1", "t", "c"));
        final IndexMetadata md = indexService.getMetadata("ns-1");
        assertThat(md.documentCount()).isEqualTo(1L);
        assertThat(md.status()).isEqualTo(IndexStatus.READY);
        assertThat(md.indexSizeBytes()).isGreaterThan(0L);
    }

    @Test
    void batchIndexProducesCorrectCount() {
        indexService.indexBatch("ns-1",
            List.of(doc("d1", "t", "c"), doc("d2", "t", "c"), doc("d3", "t", "c")));
        assertThat(indexService.getMetadata("ns-1").documentCount()).isEqualTo(3L);
    }

    @Test
    void deleteDecrementsCount() {
        indexService.indexBatch("ns-1", List.of(doc("d1", "t", "c"), doc("d2", "t", "c")));
        assertThat(indexService.delete("ns-1", "d1")).isTrue();
        assertThat(indexService.getMetadata("ns-1").documentCount()).isEqualTo(1L);
    }

    @Test
    void rebuildClearsIndex() {
        indexService.indexBatch("ns-1", List.of(doc("d1", "t", "c"), doc("d2", "t", "c")));
        indexService.rebuild("ns-1");
        assertThat(indexService.getMetadata("ns-1").documentCount()).isZero();
    }

    @Test
    void unknownNamespaceThrows() {
        assertThatThrownBy(() -> indexService.index(doc("d", "t", "c").toBuilder()
            .namespaceId("missing").build()))
            .isInstanceOf(NoSuchElementException.class);
    }
}
