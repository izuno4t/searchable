package io.searchable.core.infrastructure.lucene;

import io.searchable.core.domain.document.Document;
import io.searchable.core.infrastructure.embedding.HashEmbeddingProvider;
import org.apache.lucene.search.IndexSearcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for the timestamp-versioned layout and the
 * zero-downtime rebuild flow on {@link LuceneIndexProvider}.
 */
class LuceneIndexProviderRebuildTest {

    @TempDir Path tempDir;

    private LuceneIndexProvider provider;
    private LuceneIndexer indexer;

    @BeforeEach
    void setUp() {
        provider = new LuceneIndexProvider(
            new IndexLayout(tempDir),
            AnalyzerFactory.japanese(),
            false,
            StorageBackend.FILESYSTEM,
            Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC),
            // shorten the grace + stale window so the test does not hang
            Duration.ofMillis(50),
            Duration.ofMinutes(5));
        indexer = new LuceneIndexer(provider, new HashEmbeddingProvider(64));
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    @Test
    void firstAcquisitionCreatesAndPromotesAnInitialVersion() {
        provider.getOrCreate("ns");
        final IndexLayout layout = new IndexLayout(tempDir);
        // After the first open, exactly one readable version exists.
        assertThat(layout.readableVersions("ns")).hasSize(1);
        assertThat(layout.latestReadable("ns")).isPresent();
    }

    @Test
    void buildPromotionSwapsTheLiveContextAtomically() throws Exception {
        // Seed the live context with one document.
        indexer.index(Document.builder()
            .id("d1").namespaceId("ns").title("t").content("v1").build());

        final LuceneIndexContext before = provider.getOrCreate("ns");
        final long beforeCount = before.documentCount();
        assertThat(beforeCount).isEqualTo(1L);

        // Run a rebuild: empty build, promote.
        final LuceneIndexProvider.BuildHandle handle = provider.beginBuild("ns");
        provider.completeBuild(handle);

        final LuceneIndexContext after = provider.getOrCreate("ns");
        assertThat(after).isNotSameAs(before);
        assertThat(after.documentCount()).isZero();

        // Old searcher remains usable until the grace period elapses
        // (the test grace is 50ms; we just check the searcher hasn't been
        // closed under our feet immediately).
        final IndexSearcher searcher = before.acquireSearcher();
        try {
            assertThat(searcher.getIndexReader().numDocs()).isEqualTo(1L);
        } finally {
            before.release(searcher);
        }
    }

    @Test
    void multipleRebuildsKeepLatestReadablePointingAtMostRecent() {
        provider.getOrCreate("ns");
        final IndexLayout layout = new IndexLayout(tempDir);
        final long initial = IndexLayout.versionOf(layout.latestReadable("ns").orElseThrow());

        for (int i = 0; i < 3; i++) {
            final LuceneIndexProvider.BuildHandle h = provider.beginBuild("ns");
            provider.completeBuild(h);
        }

        final long latest = IndexLayout.versionOf(layout.latestReadable("ns").orElseThrow());
        assertThat(latest).isGreaterThan(initial);
    }

    @Test
    void refreshReopensReadOnlyContextOnPromotedVersion() throws Exception {
        // Writer-side: seed an initial version, index a doc, capture path.
        indexer.index(Document.builder()
            .id("d1").namespaceId("ns").title("t").content("v1").build());
        final Path initialVersion = provider.getOrCreate("ns").versionDir().orElseThrow();

        // Reader-side: a separate provider opened in read-only mode picks
        // up the same initial version.
        try (LuceneIndexProvider reader = new LuceneIndexProvider(
                new IndexLayout(tempDir),
                AnalyzerFactory.japanese(),
                true,
                StorageBackend.FILESYSTEM,
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMillis(50),
                Duration.ofMinutes(5))) {

            final LuceneIndexContext readerCtx = reader.getOrCreate("ns");
            assertThat(readerCtx.versionDir()).contains(initialVersion);
            assertThat(readerCtx.documentCount()).isEqualTo(1L);

            // Writer-side: full rebuild to a fresh <ts>/.
            final int written = indexer.rebuild("ns", java.util.List.of(
                Document.builder().id("d2").namespaceId("ns")
                    .title("t").content("v2").build(),
                Document.builder().id("d3").namespaceId("ns")
                    .title("t").content("v3").build()));
            assertThat(written).isEqualTo(2);

            final Path promotedVersion = provider.getOrCreate("ns").versionDir().orElseThrow();
            assertThat(promotedVersion).isNotEqualTo(initialVersion);

            // Reader-side refresh: detects the promotion and reopens.
            assertThat(reader.refresh("ns")).isTrue();
            final LuceneIndexContext reopened = reader.getOrCreate("ns");
            assertThat(reopened.versionDir()).contains(promotedVersion);
            assertThat(reopened.documentCount()).isEqualTo(2L);
        }
    }

    @Test
    void refreshIsNoOpWhenNoPromotionOccurred() throws Exception {
        indexer.index(Document.builder()
            .id("d1").namespaceId("ns").title("t").content("v1").build());

        try (LuceneIndexProvider reader = new LuceneIndexProvider(
                new IndexLayout(tempDir),
                AnalyzerFactory.japanese(),
                true,
                StorageBackend.FILESYSTEM,
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMillis(50),
                Duration.ofMinutes(5))) {
            final LuceneIndexContext before = reader.getOrCreate("ns");
            final Path beforeDir = before.versionDir().orElseThrow();

            // No build/promotion between opens: refresh should not swap.
            assertThat(reader.refresh("ns")).isTrue();
            final LuceneIndexContext after = reader.getOrCreate("ns");
            assertThat(after).isSameAs(before);
            assertThat(after.versionDir()).contains(beforeDir);
        }
    }

    @Test
    void cancelBuildDoesNotAffectLiveContext() {
        final LuceneIndexContext before = provider.getOrCreate("ns");

        final LuceneIndexProvider.BuildHandle handle = provider.beginBuild("ns");
        provider.cancelBuild(handle);

        final LuceneIndexContext after = provider.getOrCreate("ns");
        // Live context unchanged (no swap on cancel).
        assertThat(after).isSameAs(before);
        // The .tmp directory has been removed.
        final IndexLayout layout = new IndexLayout(tempDir);
        assertThat(layout.readableVersions("ns")).hasSize(1);
    }
}
