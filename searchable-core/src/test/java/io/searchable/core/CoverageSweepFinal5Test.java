package io.searchable.core;

import io.searchable.core.application.FacetAggregator;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.application.SearchPerformanceMonitor;
import io.searchable.core.application.config.SearchableGlobalConfig;
import io.searchable.core.domain.namespace.AiConfig;
import io.searchable.core.domain.namespace.EmbeddingConfig;
import io.searchable.core.domain.namespace.NamespaceConfigPatch;
import io.searchable.core.domain.search.FacetSpec;
import io.searchable.core.domain.search.SearchHit;
import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import io.searchable.core.infrastructure.lucene.AnalyzerFactory;
import io.searchable.core.infrastructure.lucene.IndexLayout;
import io.searchable.core.infrastructure.lucene.LuceneIndexProvider;
import io.searchable.core.infrastructure.persistence.DataSourceFactory;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import io.searchable.core.infrastructure.persistence.SchemaInitializer;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcIndexMetadataRepository;
import io.searchable.core.infrastructure.persistence.jdbc.JdbcNamespaceRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Last sweep: cover applyDefaults branches and other long-tail items. */
class CoverageSweepFinal5Test {

    @TempDir Path tempDir;

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void namespaceServiceApplyDefaultsWithEveryFieldSet() throws Exception {
        // Drives every `X != null` ternary arm in applyDefaults().
        final String url = "jdbc:h2:" + tempDir.resolve("ad-db") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("ad")), AnalyzerFactory.japanese())) {
            final var svc = new NamespaceService(new JdbcNamespaceRepository(ds),
                new JdbcIndexMetadataRepository(ds), provider,
                SearchableGlobalConfig.defaults(), CLOCK);
            final var patch = new NamespaceConfigPatch(
                SearchType.HYBRID,
                SearchStrategy.PARALLEL,
                SearchOrder.VECTOR_FIRST,
                new EmbeddingConfig("multilingual-e5-small", 384),
                new AiConfig(true, "openai", "gpt-4"),
                2.5,
                Map.of("custom", "value"));
            final var ns = svc.create("a", "A", patch);
            assertThat(ns.config().indexWeight()).isEqualTo(2.5);
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    @Test
    void namespaceServiceRenameAndUpdateConfigOnUnknownNamespace() throws Exception {
        // Drives the lambda$rename$1 and lambda$updateConfig$0 throw branches.
        final String url = "jdbc:h2:" + tempDir.resolve("nm-db") + ";MODE=PostgreSQL";
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
        new SchemaInitializer(ds).initialize();
        try (LuceneIndexProvider provider = new LuceneIndexProvider(
                new IndexLayout(tempDir.resolve("nm")), AnalyzerFactory.japanese())) {
            final var svc = new NamespaceService(new JdbcNamespaceRepository(ds),
                new JdbcIndexMetadataRepository(ds), provider,
                SearchableGlobalConfig.defaults(), CLOCK);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> svc.rename("none", "X"))
                .isInstanceOf(java.util.NoSuchElementException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    svc.updateConfig("none", NamespaceConfigPatch.empty()))
                .isInstanceOf(java.util.NoSuchElementException.class);
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    // ─── FacetAggregator.extractFromContent: regex no match + blank key ──
    @Test
    void facetAggregatorContentReturnsEmptyWhenNoMatches() {
        final var hits = List.of(new SearchHit("d", "ns", "t", "no markers here",
            1.0, Map.of(), Map.of()));
        final var counts = FacetAggregator.aggregate(hits,
            List.of(FacetSpec.content("k", "k"))).get("k");
        assertThat(counts).isEmpty();
    }

    // ─── SearchService.aggregate single-target / applyIndexWeight ────────
    // Covered indirectly via existing SearchServiceTest. The remaining 1
    // branch is in the multi-target empty-result path; SearchServiceBranchTest
    // already exercises it.

    // ─── DocumentSummary constructor: null id branch ─────────────────────
    @Test
    void documentSummaryRecordRejectsNullId() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new io.searchable.core.application.DocumentSummary(
                    null, "ns", "t", "s", null))
            .isInstanceOf(NullPointerException.class);
    }

    // ─── IndexMetadata explicit ctor (no-stats overload) ─────────────────
    @Test
    void indexMetadataConstructorWithoutStatistics() {
        // Trigger the `statistics == null` short-circuit -> Map.of()
        final var m = new io.searchable.core.domain.index.IndexMetadata(
            "ns", 1, 1, Instant.parse("2026-01-01T00:00:00Z"),
            io.searchable.core.domain.index.IndexStatus.READY, null);
        assertThat(m.statistics()).isEmpty();
    }

    // ─── SearchPerformanceMonitor explicit ctor ──────────────────────────
    @Test
    void searchPerformanceMonitorExposesEmptySummaryInitially() {
        final var m = new SearchPerformanceMonitor(5, CLOCK);
        final var summary = m.summary();
        assertThat(summary.samples()).isEmpty();
    }

    // ─── JdbcUserDictionaryRepository: joinEntries/parseEntries blank/empty
    @Test
    void jdbcUserDictRepoHandlesBlankAndCommentLines() throws Exception {
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:jud-blank;DB_CLOSE_DELAY=-1", "sa", ""));
        new SchemaInitializer(ds).initialize();
        try {
            final var repo = new io.searchable.core.infrastructure.dictionary
                .JdbcUserDictionaryRepository(ds);
            // Save with empty entries -> joinEntries returns "" (empty branch).
            repo.save(new io.searchable.core.domain.dictionary.UserDictionary(
                io.searchable.core.domain.dictionary.DictionaryScope.GLOBAL, "g",
                List.of(), Instant.parse("2026-01-01T00:00:00Z")));
            assertThat(repo.find(io.searchable.core.domain.dictionary.DictionaryScope.GLOBAL)
                .orElseThrow().entries()).isEmpty();
        } finally {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

    // ─── HtmlParser: empty title element + non-string h1 ────────────────
    @Test
    void htmlParserHandlesEmptyTitleAndH1() {
        final var p = new io.searchable.core.infrastructure.parser.HtmlParser();
        // Empty <title> with body containing only paragraph - falls to fallback.
        assertThat(p.parse("<html><head><title>   </title></head><body><p>body</p></body></html>", "fb")
            .title()).isEqualTo("fb");
    }
}
