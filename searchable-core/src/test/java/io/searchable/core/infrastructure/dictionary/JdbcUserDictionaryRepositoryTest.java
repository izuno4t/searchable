package io.searchable.core.infrastructure.dictionary;

import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import io.searchable.core.testing.H2TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcUserDictionaryRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private H2TestDatabase db;
    private JdbcUserDictionaryRepository repository;

    @BeforeEach
    void setUp() {
        db = H2TestDatabase.open();
        repository = new JdbcUserDictionaryRepository(db.dataSource());
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private UserDictionaryEntry entry(final String surface) {
        return new UserDictionaryEntry(surface, surface, "ヨミ", "POS");
    }

    @Test
    void saveAndFindRoundTripsGlobalDictionary() {
        repository.save(new UserDictionary(DictionaryScope.GLOBAL, "global",
            List.of(entry("Lucene"), entry("形態素")), T0));

        final UserDictionary loaded = repository.find(DictionaryScope.GLOBAL).orElseThrow();
        assertThat(loaded.scope()).isEqualTo(DictionaryScope.GLOBAL);
        assertThat(loaded.name()).isEqualTo("global");
        assertThat(loaded.entries()).extracting(UserDictionaryEntry::surface)
            .containsExactly("Lucene", "形態素");
    }

    @Test
    void namespaceDictionaryUsesNamespaceScope() {
        repository.save(new UserDictionary(DictionaryScope.namespace("project-a"),
            "Project A", List.of(entry("社内用語")), T0));

        final UserDictionary loaded =
            repository.find(DictionaryScope.namespace("project-a")).orElseThrow();
        assertThat(loaded.scope().key()).isEqualTo("NAMESPACE:project-a");
    }

    @Test
    void saveActsAsUpsert() {
        repository.save(new UserDictionary(DictionaryScope.GLOBAL, "old",
            List.of(entry("a")), T0));
        repository.save(new UserDictionary(DictionaryScope.GLOBAL, "new",
            List.of(entry("b"), entry("c")), T0));

        final UserDictionary loaded = repository.find(DictionaryScope.GLOBAL).orElseThrow();
        assertThat(loaded.name()).isEqualTo("new");
        assertThat(loaded.entries()).hasSize(2);
    }

    @Test
    void findAllReturnsAllScopesOrdered() {
        repository.save(new UserDictionary(DictionaryScope.GLOBAL, "g",
            List.of(entry("a")), T0));
        repository.save(new UserDictionary(DictionaryScope.namespace("z"), "z",
            List.of(entry("b")), T0));
        repository.save(new UserDictionary(DictionaryScope.namespace("a"), "a",
            List.of(entry("c")), T0));

        assertThat(repository.findAll()).extracting(d -> d.scope().key())
            .containsExactly("GLOBAL", "NAMESPACE:a", "NAMESPACE:z");
    }

    @Test
    void deleteReturnsTrueOnlyWhenRowExisted() {
        assertThat(repository.delete(DictionaryScope.GLOBAL)).isFalse();
        repository.save(new UserDictionary(DictionaryScope.GLOBAL, "g",
            List.of(entry("x")), T0));
        assertThat(repository.delete(DictionaryScope.GLOBAL)).isTrue();
        assertThat(repository.find(DictionaryScope.GLOBAL)).isEmpty();
    }
}
