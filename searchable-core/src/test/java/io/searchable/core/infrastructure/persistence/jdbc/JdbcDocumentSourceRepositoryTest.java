package io.searchable.core.infrastructure.persistence.jdbc;

import io.searchable.core.domain.document.DocumentSource;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDocumentSourceRepositoryTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private JdbcDocumentSourceRepository repo;
    private JdbcNamespaceRepository nsRepo;
    private static final Instant NOW = Instant.parse("2026-05-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        nsRepo = new JdbcNamespaceRepository(dataSource);
        repo = new JdbcDocumentSourceRepository(dataSource);
        // FK requires a namespace row first
        nsRepo.save(new Namespace("ns", "n", NamespaceConfig.defaults(), NOW, NOW));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void rejectsNullArgs() {
        assertThatThrownBy(() -> new JdbcDocumentSourceRepository(null))
            .isInstanceOf(NullPointerException.class);
        final DocumentSource src = DocumentSource.of("file", "/x");
        assertThatThrownBy(() -> repo.save(null, "d", src)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.save("ns", null, src)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.save("ns", "d", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.findByDocumentId(null, "d")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.findByDocumentId("ns", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.findByNamespace(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.delete(null, "d")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.delete("ns", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void saveAndFindFullSource() {
        final DocumentSource source = new DocumentSource("file", "/path/x",
            "abc123", Instant.parse("2026-05-15T00:00:00Z"));
        repo.save("ns", "doc-1", source);

        final Optional<DocumentSource> found = repo.findByDocumentId("ns", "doc-1");
        assertThat(found).isPresent();
        assertThat(found.get().type()).isEqualTo("file");
        assertThat(found.get().contentHash()).isEqualTo("abc123");
        assertThat(found.get().sourceUpdated()).isEqualTo("2026-05-15T00:00:00Z");
    }

    @Test
    void saveHandlesNullableFields() {
        final DocumentSource source = new DocumentSource("file", "/y", null, null);
        repo.save("ns", "doc-2", source);

        final DocumentSource found = repo.findByDocumentId("ns", "doc-2").orElseThrow();
        assertThat(found.contentHash()).isNull();
        assertThat(found.sourceUpdated()).isNull();
    }

    @Test
    void findReturnsEmptyForMissingDocument() {
        assertThat(repo.findByDocumentId("ns", "never")).isEmpty();
    }

    @Test
    void findByNamespaceReturnsAllRowsOrdered() {
        repo.save("ns", "b", DocumentSource.of("file", "/b"));
        repo.save("ns", "a", DocumentSource.of("file", "/a"));
        repo.save("ns", "c", DocumentSource.of("file", "/c"));

        final List<DocumentSource> all = repo.findByNamespace("ns");
        assertThat(all).extracting(DocumentSource::location)
            .containsExactly("/a", "/b", "/c");
    }

    @Test
    void deleteReturnsTrueOnlyWhenRowExists() {
        repo.save("ns", "del", DocumentSource.of("file", "/d"));
        assertThat(repo.delete("ns", "del")).isTrue();
        assertThat(repo.delete("ns", "del")).isFalse();
    }

    @Test
    void mergeOverwritesExistingRow() {
        repo.save("ns", "m", new DocumentSource("file", "/v1", "h1", null));
        repo.save("ns", "m", new DocumentSource("url", "/v2", "h2", null));
        final DocumentSource latest = repo.findByDocumentId("ns", "m").orElseThrow();
        assertThat(latest.type()).isEqualTo("url");
        assertThat(latest.contentHash()).isEqualTo("h2");
    }

    @Test
    void sqlExceptionWrappedAsIllegalStateOnSave() throws Exception {
        // Drop the table to provoke a SQL exception on every method.
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE DOCUMENT_SOURCE");
        }
        assertThatThrownBy(() -> repo.save("ns", "x", DocumentSource.of("file", "/x")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repo.findByDocumentId("ns", "x"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repo.findByNamespace("ns"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repo.delete("ns", "x"))
            .isInstanceOf(IllegalStateException.class);
    }
}
