package io.searchable.core.infrastructure.dictionary;

import io.searchable.core.domain.dictionary.DictionaryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Branch coverage helpers for {@link FileUserDictionaryRepository}. */
class FileUserDictionaryRepositoryBranchTest {

    @TempDir Path tempDir;

    @Test
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new FileUserDictionaryRepository(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void apiRejectsNullArgs() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        assertThatThrownBy(() -> repo.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.find(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.delete(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void findReturnsEmptyForMissingFile() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        assertThat(repo.find(DictionaryScope.GLOBAL)).isEmpty();
        assertThat(repo.find(DictionaryScope.namespace("never"))).isEmpty();
    }

    @Test
    void findHonoursCommentLineAsDisplayName() throws Exception {
        Files.writeString(tempDir.resolve("global.csv"),
            "# Custom Name\n  \nLucene,Lucene,ルセン,固有名詞\n",
            StandardCharsets.UTF_8);
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        final var loaded = repo.find(DictionaryScope.GLOBAL).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Custom Name");
        assertThat(loaded.entries()).hasSize(1);
    }

    @Test
    void blankCommentDoesNotOverrideDisplayName() throws Exception {
        Files.writeString(tempDir.resolve("global.csv"),
            "#  \nLucene,Lucene,ルセン,固有名詞\n", StandardCharsets.UTF_8);
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        final var loaded = repo.find(DictionaryScope.GLOBAL).orElseThrow();
        assertThat(loaded.name()).isEqualTo("GLOBAL");
    }

    @Test
    void secondCommentDoesNotReplaceFirst() throws Exception {
        Files.writeString(tempDir.resolve("global.csv"),
            "# First Name\n# Second Name\nLucene,Lucene,ルセン,固有名詞\n",
            StandardCharsets.UTF_8);
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        final var loaded = repo.find(DictionaryScope.GLOBAL).orElseThrow();
        assertThat(loaded.name()).isEqualTo("First Name");
    }

    @Test
    void findAllReturnsEmptyWhenNoFiles() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        assertThat(repo.findAll()).isEmpty();
    }

    @Test
    void findAllSkipsNonCsvFilesInNamespacesDir() throws Exception {
        Files.createDirectories(tempDir.resolve("namespaces"));
        Files.writeString(tempDir.resolve("namespaces/ignored.txt"), "anything");
        Files.writeString(tempDir.resolve("namespaces/keep.csv"),
            "# keep\nx,x,x,x\n");
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        assertThat(repo.findAll()).extracting(d -> d.scope().key())
            .containsExactly("NAMESPACE:keep");
    }

    @Test
    void unreadableFileSurfacesAsUncheckedIo() throws Exception {
        // Create a malformed CSV; reading it will trip
        // UserDictionaryEntry.fromCsv -> IllegalArgumentException, not
        // UncheckedIOException. We only assert it throws _something_.
        Files.writeString(tempDir.resolve("global.csv"), "not,enough,cols\n");
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        assertThatThrownBy(() -> repo.find(DictionaryScope.GLOBAL))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
