package io.searchable.core.application;

import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import io.searchable.core.domain.dictionary.UserDictionaryResolver;
import io.searchable.core.domain.namespace.NamespaceRepository;
import io.searchable.core.infrastructure.dictionary.JdbcUserDictionaryRepository;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.lucene.UserDictionaryAnalyzerFactory;
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

import static org.assertj.core.api.Assertions.assertThat;

class DictionaryServiceTest {

    @TempDir Path tempDir;

    private DataSource dataSource;
    private LuceneIndexProvider provider;
    private DictionaryService service;
    private NamespaceService namespaceService;
    private NamespaceRepository namespaces;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("md") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(dataSource).initialize();
        namespaces = new JdbcNamespaceRepository(dataSource);
        final var dictionaryRepo = new JdbcUserDictionaryRepository(dataSource);
        final var resolver = new UserDictionaryResolver(dictionaryRepo);
        provider = new LuceneIndexProvider(
            new IndexLayout(tempDir.resolve("indexes")),
            new UserDictionaryAnalyzerFactory(resolver));
        namespaceService = new NamespaceService(namespaces,
            new JdbcIndexMetadataRepository(dataSource),
            provider, SearchableGlobalConfig.defaults(),
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC));
        service = new DictionaryService(dictionaryRepo, provider, namespaces);
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }

    @Test
    void savingNamespaceScopedDictionaryReopensIndex() {
        namespaceService.create("dict_ns", "Dict", null);
        // Trigger initial context open.
        provider.getOrCreate("dict_ns");
        assertThat(provider.isOpen("dict_ns")).isTrue();

        service.save(new UserDictionary(
            DictionaryScope.namespace("dict_ns"),
            "test",
            List.of(new UserDictionaryEntry("用語", "用語", "ヨウゴ", "カスタム名詞")),
            Instant.parse("2026-05-15T00:00:00Z")));

        // After refresh the context is still open (refresh reopens it).
        assertThat(provider.isOpen("dict_ns")).isTrue();
        assertThat(service.find(DictionaryScope.namespace("dict_ns"))).isPresent();
    }

    @Test
    void savingGlobalDictionaryReopensAllOpenNamespaces() {
        namespaceService.create("g_a", "A", null);
        namespaceService.create("g_b", "B", null);
        provider.getOrCreate("g_a");
        provider.getOrCreate("g_b");

        service.save(new UserDictionary(
            DictionaryScope.GLOBAL,
            "global",
            List.of(new UserDictionaryEntry("検索", "検索", "ケンサク", "カスタム名詞")),
            Instant.parse("2026-05-15T00:00:00Z")));

        assertThat(provider.isOpen("g_a")).isTrue();
        assertThat(provider.isOpen("g_b")).isTrue();
        assertThat(service.find(DictionaryScope.GLOBAL)).isPresent();
    }

    @Test
    void deletingDictionaryRefreshesAffectedNamespaces() {
        namespaceService.create("del_ns", "Del", null);
        provider.getOrCreate("del_ns");
        service.save(new UserDictionary(
            DictionaryScope.namespace("del_ns"),
            "test",
            List.of(new UserDictionaryEntry("語", "語", "ゴ", "名詞")),
            Instant.parse("2026-05-15T00:00:00Z")));

        assertThat(service.delete(DictionaryScope.namespace("del_ns"))).isTrue();
        assertThat(service.find(DictionaryScope.namespace("del_ns"))).isEmpty();
    }
}
