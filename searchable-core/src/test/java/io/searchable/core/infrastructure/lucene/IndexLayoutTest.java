package io.searchable.core.infrastructure.lucene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexLayoutTest {

    @TempDir Path tempDir;

    private static final Instant T = Instant.parse("2026-05-20T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T, ZoneOffset.UTC);

    private IndexLayout layout() {
        return new IndexLayout(tempDir);
    }

    @Test
    void rejectsInvalidNamespaceIds() {
        final IndexLayout l = layout();
        assertThatThrownBy(() -> l.namespaceDir("Has-Capitals"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> l.namespaceDir(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> l.namespaceDir(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void latestReadableIsEmptyForNewNamespace() {
        final IndexLayout l = layout();
        assertThat(l.latestReadable("ns")).isEmpty();
        assertThat(l.readableVersions("ns")).isEmpty();
    }

    @Test
    void newBuildCreatesTmpDirectoryWithClockTimestamp() throws Exception {
        final IndexLayout l = layout();
        final Path build = l.newBuild("ns", CLOCK);
        assertThat(build.getFileName().toString())
            .endsWith(IndexLayout.TMP_SUFFIX);
        assertThat(IndexLayout.versionOf(build)).isEqualTo(T.toEpochMilli());
        assertThat(Files.isDirectory(build)).isTrue();
    }

    @Test
    void newBuildMonotonicallyClampsAboveExistingVersion() throws Exception {
        final IndexLayout l = layout();
        // Existing completed version with a timestamp in the future.
        final long future = T.toEpochMilli() + 10_000L;
        final Path nsDir = l.namespaceDir("ns");
        Files.createDirectories(nsDir.resolve(Long.toString(future)));

        final Path build = l.newBuild("ns", CLOCK);
        assertThat(IndexLayout.versionOf(build)).isEqualTo(future + 1);
    }

    @Test
    void promoteAtomicallyRenamesTmpToCompletedName() throws Exception {
        final IndexLayout l = layout();
        final Path build = l.newBuild("ns", CLOCK);
        final Path promoted = l.promote(build);

        assertThat(Files.exists(build)).isFalse();
        assertThat(Files.isDirectory(promoted)).isTrue();
        assertThat(promoted.getFileName().toString())
            .doesNotEndWith(IndexLayout.TMP_SUFFIX);
    }

    @Test
    void promoteRejectsNonTmpDirectory() {
        final IndexLayout l = layout();
        final Path completed = l.namespaceDir("ns").resolve("12345");
        assertThatThrownBy(() -> l.promote(completed))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void latestReadablePicksHighestTimestamp() throws Exception {
        final IndexLayout l = layout();
        final Path nsDir = l.namespaceDir("ns");
        Files.createDirectories(nsDir.resolve("100"));
        Files.createDirectories(nsDir.resolve("200"));
        Files.createDirectories(nsDir.resolve("150"));
        // .tmp directory must be ignored when picking the readable version.
        Files.createDirectories(nsDir.resolve("999.tmp"));

        final Path latest = l.latestReadable("ns").orElseThrow();
        assertThat(latest.getFileName().toString()).isEqualTo("200");
    }

    @Test
    void obsoleteVersionsReturnsEverythingBelowKeep() throws Exception {
        final IndexLayout l = layout();
        final Path nsDir = l.namespaceDir("ns");
        Files.createDirectories(nsDir.resolve("100"));
        Files.createDirectories(nsDir.resolve("200"));
        Files.createDirectories(nsDir.resolve("300"));

        final List<Path> obsolete = l.obsoleteVersions("ns", nsDir.resolve("300"));
        assertThat(obsolete)
            .extracting(p -> p.getFileName().toString())
            .containsExactly("100", "200");
    }

    @Test
    void staleTmpDirsDetectsOrphanedDirectoryByMtime() throws Exception {
        final IndexLayout l = layout();
        final Path build = l.newBuild("ns", CLOCK);
        // Backdate the mtime to make it look orphaned.
        Files.setLastModifiedTime(build, FileTime.fromMillis(T.toEpochMilli() - 600_000L));

        final List<Path> stale = l.staleTmpDirs("ns", 60_000L, CLOCK);
        assertThat(stale).containsExactly(build);
    }

    @Test
    void staleTmpDirsSkipsRecentlyModifiedDirectory() throws Exception {
        final IndexLayout l = layout();
        final Path build = l.newBuild("ns", CLOCK);
        // mtime within the threshold window -> not yet stale.
        Files.setLastModifiedTime(build, FileTime.fromMillis(T.toEpochMilli() - 5_000L));

        final List<Path> stale = l.staleTmpDirs("ns", 60_000L, CLOCK);
        assertThat(stale).isEmpty();
    }

    @Test
    void staleTmpDirsSkipsDirectoryWithHeldWriteLock() throws Exception {
        final IndexLayout l = layout();
        final Path build = l.newBuild("ns", CLOCK);
        final Path lockFile = build.resolve("write.lock");
        Files.createFile(lockFile);
        // Set the build directory mtime AFTER creating the lock file so
        // the create-file operation does not bump it back to "now".
        final FileTime oldMtime = FileTime.fromMillis(T.toEpochMilli() - 600_000L);
        Files.setLastModifiedTime(build, oldMtime);

        try (FileChannel ch = FileChannel.open(lockFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = ch.lock()) {
            // Lock-held branch: dir is not collectible even though the
            // mtime is stale.
            assertThat(l.staleTmpDirs("ns", 60_000L, CLOCK)).isEmpty();
        }
        // Re-stamp mtime after the lock close (which may have touched the
        // lock file) and verify the dir becomes collectible.
        Files.setLastModifiedTime(build, oldMtime);
        assertThat(l.staleTmpDirs("ns", 60_000L, CLOCK)).containsExactly(build);
    }

    @Test
    void deleteRecursivelyRemovesNestedTree() throws Exception {
        final IndexLayout l = layout();
        final Path build = l.newBuild("ns", CLOCK);
        Files.createDirectories(build.resolve("nested"));
        Files.writeString(build.resolve("nested/segments_1"), "x");
        l.deleteRecursively(build);
        assertThat(Files.exists(build)).isFalse();
    }

    @Test
    void deleteRecursivelyIsNoOpOnMissingPath() throws IOException {
        layout().deleteRecursively(tempDir.resolve("never-there"));
    }

    @Test
    void versionOfRejectsInvalidName() {
        assertThatThrownBy(() -> IndexLayout.versionOf(tempDir.resolve("not-a-number")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void versionOfAcceptsTmpDirectory() {
        assertThat(IndexLayout.versionOf(tempDir.resolve("12345" + IndexLayout.TMP_SUFFIX)))
            .isEqualTo(12345L);
    }

    @Test
    void newBuildIgnoresNonNumericChildren() throws Exception {
        final IndexLayout l = layout();
        Files.createDirectories(l.namespaceDir("ns").resolve("garbage"));
        // Should not throw -- non-numeric child is skipped.
        final Path build = l.newBuild("ns", CLOCK);
        assertThat(IndexLayout.versionOf(build)).isEqualTo(T.toEpochMilli());
    }
}
