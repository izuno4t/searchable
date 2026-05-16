package io.searchable.core.infrastructure.dictionary;

import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileUserDictionaryRepositoryTest {

    @TempDir Path tempDir;

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private UserDictionaryEntry entry(final String surface) {
        return new UserDictionaryEntry(surface, surface, "ヨミ", "POS");
    }

    @Test
    void savedGlobalDictionaryCanBeReloaded() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global",
            List.of(entry("Lucene"), entry("形態素")), T0));

        assertThat(Files.exists(tempDir.resolve("global.csv"))).isTrue();
        final UserDictionary loaded = repo.find(DictionaryScope.GLOBAL).orElseThrow();
        assertThat(loaded.entries()).extracting(UserDictionaryEntry::surface)
            .containsExactly("Lucene", "形態素");
    }

    @Test
    void namespaceDictionaryIsStoredInSubdirectory() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        repo.save(new UserDictionary(DictionaryScope.namespace("project-a"), "Project A",
            List.of(entry("社内用語")), T0));

        assertThat(Files.exists(tempDir.resolve("namespaces/project-a.csv"))).isTrue();
        final UserDictionary loaded =
            repo.find(DictionaryScope.namespace("project-a")).orElseThrow();
        assertThat(loaded.entries()).hasSize(1);
        assertThat(loaded.entries().get(0).surface()).isEqualTo("社内用語");
        assertThat(loaded.name()).isEqualTo("Project A");
    }

    @Test
    void findAllReturnsGlobalAndAllNamespaces() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global",
            List.of(entry("a")), T0));
        repo.save(new UserDictionary(DictionaryScope.namespace("ns-1"), "ns-1",
            List.of(entry("b")), T0));
        repo.save(new UserDictionary(DictionaryScope.namespace("ns-2"), "ns-2",
            List.of(entry("c")), T0));

        assertThat(repo.findAll()).extracting(d -> d.scope().key())
            .containsExactlyInAnyOrder("GLOBAL", "NAMESPACE:ns-1", "NAMESPACE:ns-2");
    }

    @Test
    void deleteReturnsTrueOnlyWhenFileExisted() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        assertThat(repo.delete(DictionaryScope.GLOBAL)).isFalse();
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global",
            List.of(entry("x")), T0));
        assertThat(repo.delete(DictionaryScope.GLOBAL)).isTrue();
        assertThat(repo.find(DictionaryScope.GLOBAL)).isEmpty();
    }

    @Test
    void commentLinesAreIgnoredOnRead() {
        final FileUserDictionaryRepository repo = new FileUserDictionaryRepository(tempDir);
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global", List.of(), T0));

        final UserDictionary loaded = repo.find(DictionaryScope.GLOBAL).orElseThrow();
        assertThat(loaded.entries()).isEmpty();
        assertThat(loaded.name()).isEqualTo("global");
    }
}
