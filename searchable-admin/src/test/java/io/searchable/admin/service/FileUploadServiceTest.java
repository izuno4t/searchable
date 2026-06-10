package io.searchable.admin.service;

import io.searchable.core.application.IndexService;
import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.domain.embedding.EmbeddingProvider;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider provider;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        provider = new LuceneIndexProvider(new IndexLayout(tempDir.resolve("indexes")),
            AnalyzerFactory.japanese());
        final var nsRepo = new JdbcNamespaceRepository(dataSource);
        final var mdRepo = new JdbcIndexMetadataRepository(dataSource);
        final EmbeddingProvider embedding = new HashEmbeddingProvider(128);
        final LuceneIndexer indexer = new LuceneIndexer(provider, embedding);
        final Clock clock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        final IndexService indexService = new IndexService(nsRepo, mdRepo, provider, indexer, clock);
        new NamespaceService(nsRepo, mdRepo, provider, SearchableGlobalConfig.defaults(), clock)
            .create("up_ns", "U", null);
        service = new FileUploadService(indexService, clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void rejectsNullArgs() {
        final MockMultipartFile f = new MockMultipartFile(
            "f", "a.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.upload(null, f))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.upload("up_ns", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsEmptyFile() {
        final MockMultipartFile f = new MockMultipartFile(
            "f", "a.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.upload("up_ns", f))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void rejectsUnsupportedExtension() {
        final MockMultipartFile f = new MockMultipartFile(
            "f", "data.bin", "application/octet-stream", "abc".getBytes());
        assertThatThrownBy(() -> service.upload("up_ns", f))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported file type");
    }

    @Test
    void uploadIndexesTextFile() {
        final MockMultipartFile f = new MockMultipartFile(
            "f", "hello.txt", "text/plain", "本文の内容".getBytes(StandardCharsets.UTF_8));
        final FileUploadService.UploadResult result = service.upload("up_ns", f);
        assertThat(result.fileName()).isEqualTo("hello.txt");
        assertThat(result.parserName()).isEqualTo("plain");
        assertThat(result.status()).contains("indexed");
    }

    @Test
    void uploadHandlesAbsentOriginalFilename() {
        // null original filename triggers the "upload" fallback name in
        // FileUploadService. The fallback has no extension, so the
        // parser resolution fails with IllegalArgumentException.
        final MultipartFile f = new MockMultipartFile(
            "f", null, "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.upload("up_ns", f))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported file type");
    }

    @Test
    void wrapsIoExceptionFromInputStream() {
        final MultipartFile f = new MockMultipartFile(
            "f", "a.txt", "text/plain", "ok".getBytes()) {
            @Override public InputStream getInputStream() throws IOException {
                throw new IOException("synthetic");
            }
        };
        assertThatThrownBy(() -> service.upload("up_ns", f))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to read uploaded file");
    }
}
