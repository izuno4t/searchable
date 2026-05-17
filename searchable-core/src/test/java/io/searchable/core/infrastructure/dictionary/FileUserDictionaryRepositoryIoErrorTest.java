package io.searchable.core.infrastructure.dictionary;

import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Hits the four IOException catch branches in {@link FileUserDictionaryRepository}
 * by stubbing the static {@link Files} helpers used by each method.
 */
class FileUserDictionaryRepositoryIoErrorTest {

    @TempDir Path tempDir;

    @Test
    void saveWrapsIoException() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        final UserDictionary d = new UserDictionary(DictionaryScope.GLOBAL, "g",
            List.of(new UserDictionaryEntry("Lucene", "Lucene", "ルセン", "名詞")),
            Instant.parse("2026-01-01T00:00:00Z"));

        try (MockedStatic<Files> filesStatic = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            filesStatic.when(() -> Files.createDirectories(any(Path.class)))
                .thenThrow(new IOException("save-boom"));
            assertThatThrownBy(() -> repo.save(d))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to save dictionary");
        }
    }

    @Test
    void findWrapsIoException() throws Exception {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        Files.writeString(tempDir.resolve("global.csv"), "x,x,ヨミ,名詞\n");

        try (MockedStatic<Files> filesStatic = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            filesStatic.when(() -> Files.readAllLines(any(Path.class), any()))
                .thenThrow(new IOException("find-boom"));
            assertThatThrownBy(() -> repo.find(DictionaryScope.GLOBAL))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to read dictionary");
        }
    }

    @Test
    void findAllWrapsIoException() throws Exception {
        Files.createDirectories(tempDir.resolve("namespaces"));
        Files.writeString(tempDir.resolve("namespaces/x.csv"), "");
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);

        try (MockedStatic<Files> filesStatic = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            filesStatic.when(() -> Files.list(tempDir.resolve("namespaces")))
                .thenThrow(new IOException("list-boom"));
            assertThatThrownBy(repo::findAll)
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to list dictionaries");
        }
    }

    @Test
    void deleteWrapsIoException() throws Exception {
        Files.writeString(tempDir.resolve("global.csv"), "");
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);

        try (MockedStatic<Files> filesStatic = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            filesStatic.when(() -> Files.deleteIfExists(any(Path.class)))
                .thenThrow(new IOException("delete-boom"));
            assertThatThrownBy(() -> repo.delete(DictionaryScope.GLOBAL))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to delete dictionary");
        }
    }
}
