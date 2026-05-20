package io.searchable.core.infrastructure.persistence.jdbc;

import io.searchable.core.domain.document.DocumentMetadataRecord;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDocumentMetadataRepositoryTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private JdbcDocumentMetadataRepository repo;
    private JdbcNamespaceRepository nsRepo;
    private static final Instant T0 = Instant.parse("2026-05-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-05-02T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-05-03T00:00:00Z");

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        nsRepo = new JdbcNamespaceRepository(dataSource);
        repo = new JdbcDocumentMetadataRepository(dataSource);
        nsRepo.save(new Namespace("ns", "n", NamespaceConfig.defaults(), T0, T0));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void saveAndFindByIdRoundtripsAllFields() {
        repo.save(new DocumentMetadataRecord("ns", "doc-1", "Title",
            Map.of("url", "file:///abs/doc-1.md", "category", "guide"), T0));

        final Optional<DocumentMetadataRecord> found = repo.findById("ns", "doc-1");
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Title");
        assertThat(found.get().metadata()).containsEntry("url", "file:///abs/doc-1.md");
        assertThat(found.get().metadata()).containsEntry("category", "guide");
        assertThat(found.get().indexedAt()).isEqualTo(T0);
    }

    @Test
    void saveUpsertsExistingRow() {
        repo.save(new DocumentMetadataRecord("ns", "doc-1", "v1", Map.of("k", "old"), T0));
        repo.save(new DocumentMetadataRecord("ns", "doc-1", "v2", Map.of("k", "new"), T1));

        final DocumentMetadataRecord found = repo.findById("ns", "doc-1").orElseThrow();
        assertThat(found.title()).isEqualTo("v2");
        assertThat(found.metadata()).containsEntry("k", "new");
        assertThat(found.indexedAt()).isEqualTo(T1);
    }

    @Test
    void findByIdsBatchFetchesMatchingRowsOnly() {
        repo.save(new DocumentMetadataRecord("ns", "a", "A", Map.of(), T0));
        repo.save(new DocumentMetadataRecord("ns", "b", "B", Map.of(), T0));
        repo.save(new DocumentMetadataRecord("ns", "c", "C", Map.of(), T0));

        final List<DocumentMetadataRecord> rows = repo.findByIds("ns",
            List.of("a", "missing", "c", "a"));   // duplicates and unknown ids
        assertThat(rows).extracting(DocumentMetadataRecord::documentId)
            .containsExactlyInAnyOrder("a", "c");
    }

    @Test
    void findByIdsReturnsEmptyForEmptyInput() {
        assertThat(repo.findByIds("ns", Set.of())).isEmpty();
    }

    @Test
    void listIsOrderedByIndexedAtDescThenId() {
        repo.save(new DocumentMetadataRecord("ns", "a", "A", Map.of(), T0));
        repo.save(new DocumentMetadataRecord("ns", "b", "B", Map.of(), T2));
        repo.save(new DocumentMetadataRecord("ns", "c", "C", Map.of(), T1));

        final List<DocumentMetadataRecord> page = repo.list("ns", 0, 10);
        assertThat(page).extracting(DocumentMetadataRecord::documentId)
            .containsExactly("b", "c", "a");
    }

    @Test
    void listPaginatesViaOffsetAndLimit() {
        for (int i = 0; i < 5; i++) {
            repo.save(new DocumentMetadataRecord("ns", "id-" + i, "T" + i, Map.of(),
                T0.plusSeconds(i)));
        }
        final List<DocumentMetadataRecord> page = repo.list("ns", 2, 2);
        assertThat(page).hasSize(2);
        assertThat(page).extracting(DocumentMetadataRecord::documentId)
            .containsExactly("id-2", "id-1");   // sorted by indexedAt DESC, paginated
    }

    @Test
    void countReportsExactRowCount() {
        assertThat(repo.count("ns")).isZero();
        repo.save(new DocumentMetadataRecord("ns", "a", "A", Map.of(), T0));
        repo.save(new DocumentMetadataRecord("ns", "b", "B", Map.of(), T0));
        assertThat(repo.count("ns")).isEqualTo(2);
    }

    @Test
    void deleteRemovesOnlyTargetRow() {
        repo.save(new DocumentMetadataRecord("ns", "a", "A", Map.of(), T0));
        repo.save(new DocumentMetadataRecord("ns", "b", "B", Map.of(), T0));

        assertThat(repo.delete("ns", "a")).isTrue();
        assertThat(repo.delete("ns", "missing")).isFalse();
        assertThat(repo.findById("ns", "a")).isEmpty();
        assertThat(repo.findById("ns", "b")).isPresent();
    }

    @Test
    void deleteByNamespaceClearsAllRows() {
        repo.save(new DocumentMetadataRecord("ns", "a", "A", Map.of(), T0));
        repo.save(new DocumentMetadataRecord("ns", "b", "B", Map.of(), T0));

        repo.deleteByNamespace("ns");
        assertThat(repo.count("ns")).isZero();
    }

    @Test
    void rejectsNullArgs() {
        assertThatThrownBy(() -> new JdbcDocumentMetadataRepository(null))
            .isInstanceOf(NullPointerException.class);
        final DocumentMetadataRecord rec =
            new DocumentMetadataRecord("ns", "d", "t", Map.of(), T0);
        assertThatThrownBy(() -> repo.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.findById(null, "d")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.findById("ns", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.findByIds(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.findByIds("ns", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.list(null, 0, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.list("ns", -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repo.list("ns", 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repo.count(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.delete(null, "d")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.delete("ns", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.deleteByNamespace(null)).isInstanceOf(NullPointerException.class);
        // Sanity: valid record can save.
        repo.save(rec);
    }
}
