package io.searchable.core.application;

import io.searchable.core.domain.document.DocumentMetadataRecord;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcDocumentMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentBrowserTest {

    @TempDir Path tempDir;
    private static final Instant T0 = Instant.parse("2026-05-01T00:00:00Z");

    private DataSource dataSource;
    private JdbcDocumentMetadataRepository repo;
    private DocumentBrowser browser;

    @BeforeEach
    void setUp() {
        dataSource = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:" + tempDir.resolve("db") + ";MODE=PostgreSQL", "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        new JdbcNamespaceRepository(dataSource).save(
            new Namespace("ns", "n", NamespaceConfig.defaults(), T0, T0));
        repo = new JdbcDocumentMetadataRepository(dataSource);
        browser = new DocumentBrowser(repo);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void emptyRegistryReturnsEmptyPage() {
        final DocumentPage page = browser.list("ns", 0, 10);
        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
    }

    @Test
    void listReturnsAllDocumentsBelowLimit() {
        repo.save(new DocumentMetadataRecord("ns", "d1", "t1", Map.of(), T0));
        repo.save(new DocumentMetadataRecord("ns", "d2", "t2", Map.of(), T0.plusSeconds(1)));
        repo.save(new DocumentMetadataRecord("ns", "d3", "t3", Map.of(), T0.plusSeconds(2)));
        final DocumentPage page = browser.list("ns", 0, 10);
        assertThat(page.total()).isEqualTo(3L);
        assertThat(page.items()).extracting(DocumentSummary::id)
            .containsExactlyInAnyOrder("d1", "d2", "d3");
    }

    @Test
    void paginationReturnsRequestedSlice() {
        for (int i = 1; i <= 4; i++) {
            repo.save(new DocumentMetadataRecord("ns", "d" + i, "t" + i,
                Map.of(), T0.plusSeconds(i)));
        }
        final DocumentPage page = browser.list("ns", 1, 2);
        assertThat(page.total()).isEqualTo(4L);
        assertThat(page.items()).hasSize(2);
    }

    @Test
    void findByIdReturnsMatchingDocument() {
        repo.save(new DocumentMetadataRecord("ns", "d1", "Doc 1",
            Map.of("url", "file:///d1"), T0));
        final var found = browser.findById("ns", "d1");
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Doc 1");
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(browser.findById("ns", "missing")).isEmpty();
    }

    @Test
    void rejectsNegativeOffset() {
        assertThatThrownBy(() -> browser.list("ns", -1, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> browser.list("ns", 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
