package io.searchable.core.domain.dictionary;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserDictionaryResolverTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private UserDictionaryEntry entry(final String surface, final String seg) {
        return new UserDictionaryEntry(surface, seg, "ヨミ", "POS");
    }

    private static final class InMemoryRepo implements UserDictionaryRepository {
        private final Map<String, UserDictionary> store = new HashMap<>();

        @Override public void save(final UserDictionary d) {
            store.put(d.scope().key(), d);
        }
        @Override public Optional<UserDictionary> find(final DictionaryScope s) {
            return Optional.ofNullable(store.get(s.key()));
        }
        @Override public List<UserDictionary> findAll() {
            return new ArrayList<>(store.values());
        }
        @Override public boolean delete(final DictionaryScope s) {
            return store.remove(s.key()) != null;
        }
    }

    @Test
    void onlyGlobalIsReturnedWhenNamespaceHasNoDictionary() {
        final InMemoryRepo repo = new InMemoryRepo();
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global",
            List.of(entry("Lucene", "Lucene"), entry("形態素", "形態素")), T0));

        final UserDictionaryResolver resolver = new UserDictionaryResolver(repo);
        assertThat(resolver.resolveFor("ns-1")).extracting(UserDictionaryEntry::surface)
            .containsExactly("Lucene", "形態素");
    }

    @Test
    void namespaceEntriesAreAppended() {
        final InMemoryRepo repo = new InMemoryRepo();
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global",
            List.of(entry("Lucene", "Lucene")), T0));
        repo.save(new UserDictionary(DictionaryScope.namespace("ns-1"), "ns-1",
            List.of(entry("社内用語", "社内 用語")), T0));

        assertThat(new UserDictionaryResolver(repo).resolveFor("ns-1"))
            .extracting(UserDictionaryEntry::surface)
            .containsExactly("Lucene", "社内用語");
    }

    @Test
    void namespaceOverridesGlobalForSameSurface() {
        final InMemoryRepo repo = new InMemoryRepo();
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global",
            List.of(entry("Lucene", "Lucene")), T0));
        repo.save(new UserDictionary(DictionaryScope.namespace("ns-1"), "ns-1",
            List.of(entry("Lucene", "ル セーン")), T0));

        final List<UserDictionaryEntry> merged =
            new UserDictionaryResolver(repo).resolveFor("ns-1");
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).segmentation()).isEqualTo("ル セーン");
    }

}
