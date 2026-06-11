package io.searchable.core.infrastructure.lucene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Branch supplement for {@link IndexLayout}. Targets the validation
 * rejections, the missing-namespace early-return inside
 * {@code staleTmpDirs}, and the {@code deleteRecursively} path that
 * walks a real directory tree.
 */
class IndexLayoutExtraBranchTest {

    @TempDir Path tempDir;

    private IndexLayout layout() {
        return new IndexLayout(tempDir);
    }

    @Test
    void staleTmpDirsRejectsNegativeMillis() {
        assertThatThrownBy(() -> layout().staleTmpDirs(
            "ns", -1L, Clock.systemUTC()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("staleAfterMillis");
    }

    @Test
    void staleTmpDirsReturnsEmptyForMissingNamespace() {
        assertThat(layout().staleTmpDirs("never", 1000L, Clock.systemUTC()))
            .isEmpty();
    }

    @Test
    void staleTmpDirsFindsOldTmpDirsAndIgnoresFreshOnes() throws Exception {
        final Instant now = Instant.parse("2026-05-15T00:00:00Z");
        final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        final IndexLayout l = layout();
        final Path nsDir = l.namespaceDir("stale-ns");

        // Stale .tmp: older than the cutoff.
        final Path stale = nsDir.resolve("1000.tmp");
        Files.createDirectories(stale);
        Files.setLastModifiedTime(stale,
            java.nio.file.attribute.FileTime.from(now.minus(Duration.ofHours(2))));

        // Fresh .tmp: newer than the cutoff.
        final Path fresh = nsDir.resolve("2000.tmp");
        Files.createDirectories(fresh);
        Files.setLastModifiedTime(fresh,
            java.nio.file.attribute.FileTime.from(now.minus(Duration.ofSeconds(1))));

        // Non-tmp directory: must be ignored entirely.
        Files.createDirectories(nsDir.resolve("3000"));
        // Plain file: must be ignored entirely.
        Files.writeString(nsDir.resolve("readme.txt"), "ignore me");

        final var stales = l.staleTmpDirs(
            "stale-ns", Duration.ofHours(1).toMillis(), clock);
        assertThat(stales).contains(stale);
        assertThat(stales).doesNotContain(fresh);
    }

    @Test
    void deleteRecursivelyIsNoOpForMissingPath() {
        layout().deleteRecursively(tempDir.resolve("never-existed"));
    }

    @Test
    void deleteRecursivelyRemovesNestedDirectoryTree() throws Exception {
        final Path root = tempDir.resolve("to-delete");
        Files.createDirectories(root.resolve("a/b/c"));
        Files.writeString(root.resolve("a/file.txt"), "x");
        Files.writeString(root.resolve("a/b/c/leaf.txt"), "y");

        layout().deleteRecursively(root);

        assertThat(root).doesNotExist();
    }

    @Test
    void versionOfParsesTimestampFromVersionDirName() {
        assertThat(IndexLayout.versionOf(tempDir.resolve("1781094174107")))
            .isEqualTo(1781094174107L);
    }

    @Test
    void versionOfRejectsNonNumericName() {
        assertThatThrownBy(() -> IndexLayout.versionOf(tempDir.resolve("not-a-number")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void versionOfRejectsNull() {
        assertThatThrownBy(() -> IndexLayout.versionOf(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteRecursivelyRejectsNullPath() {
        assertThatThrownBy(() -> layout().deleteRecursively(null))
            .isInstanceOf(NullPointerException.class);
    }
}
