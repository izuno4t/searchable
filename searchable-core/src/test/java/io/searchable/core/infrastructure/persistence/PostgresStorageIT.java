package io.searchable.core.infrastructure.persistence;

import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexStatus;
import io.searchable.core.domain.namespace.Namespace;
import io.searchable.core.domain.namespace.NamespaceConfig;
import io.searchable.core.infrastructure.dictionary.JdbcUserDictionaryRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the JDBC storage backend against a real PostgreSQL
 * container (Testcontainers). Skipped when Docker is unavailable.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("io.searchable.core.infrastructure.persistence.PostgresStorageIT#dockerAvailable")
class PostgresStorageIT {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    private PostgreSQLContainer<?> container;
    private DataSource dataSource;
    private PersistenceConfig config;

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeAll
    void startContainer() {
        container = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("searchable")
            .withUsername("searchable")
            .withPassword("searchable");
        container.start();
        config = new PersistenceConfig("POSTGRESQL",
            container.getJdbcUrl(), container.getUsername(), container.getPassword(), 4);
        dataSource = DataSourceFactory.create(config);
        new SchemaInitializer(dataSource).initialize();
    }

    @AfterAll
    void stopContainer() throws Exception {
        if (dataSource instanceof AutoCloseable c) {
            c.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void resetTables() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM USER_DICTIONARY");
            s.execute("DELETE FROM DOCUMENT_SOURCE");
            s.execute("DELETE FROM INDEX_METADATA");
            s.execute("DELETE FROM NAMESPACE");
        }
    }

    @Test
    void schemaInitializerCreatesTablesIdempotently() {
        // initialize() in @BeforeAll already ran; calling again must not fail.
        new SchemaInitializer(dataSource).initialize();
    }

    @Test
    void namespaceRepositoryRoundTripWorksOnPostgres() {
        final JdbcNamespaceRepository repo = new JdbcNamespaceRepository(dataSource);
        final Namespace ns = new Namespace(
            "pg_ns_1", "Postgres NS",
            NamespaceConfig.defaults(),
            Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-05-01T00:00:00Z"));
        repo.save(ns);

        assertThat(repo.exists("pg_ns_1")).isTrue();
        assertThat(repo.findById("pg_ns_1")).isPresent();
        assertThat(repo.findAll()).extracting(Namespace::id).contains("pg_ns_1");
        assertThat(repo.delete("pg_ns_1")).isTrue();
        assertThat(repo.findById("pg_ns_1")).isEmpty();
    }

    @Test
    void indexMetadataRepositoryRoundTripWorksOnPostgres() {
        final JdbcNamespaceRepository nsRepo = new JdbcNamespaceRepository(dataSource);
        final JdbcIndexMetadataRepository metaRepo = new JdbcIndexMetadataRepository(dataSource);
        final Instant t = Instant.parse("2026-05-02T00:00:00Z");

        nsRepo.save(new Namespace("pg_ns_2", "NS", NamespaceConfig.defaults(), t, t));
        metaRepo.save(new IndexMetadata("pg_ns_2", 100L, 4096L, t, IndexStatus.READY, Map.of()));

        final IndexMetadata loaded = metaRepo.findByNamespaceId("pg_ns_2").orElseThrow();
        assertThat(loaded.documentCount()).isEqualTo(100L);
        assertThat(loaded.indexSizeBytes()).isEqualTo(4096L);
        assertThat(loaded.status()).isEqualTo(IndexStatus.READY);
    }

    @Test
    void userDictionaryRepositoryRoundTripWorksOnPostgres() {
        final JdbcUserDictionaryRepository repo = new JdbcUserDictionaryRepository(dataSource);
        final UserDictionary dict = new UserDictionary(
            DictionaryScope.namespace("pg_ns_3"),
            "プロジェクト辞書",
            List.of(new UserDictionaryEntry(
                "Searchable", "Searchable", "サーチャブル", "カスタム名詞")),
            Instant.parse("2026-05-03T00:00:00Z"));

        repo.save(dict);
        assertThat(repo.find(DictionaryScope.namespace("pg_ns_3"))).isPresent();
        assertThat(repo.delete(DictionaryScope.namespace("pg_ns_3"))).isTrue();
    }
}
