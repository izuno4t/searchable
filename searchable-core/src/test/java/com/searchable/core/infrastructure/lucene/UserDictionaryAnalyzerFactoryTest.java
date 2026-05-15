package com.searchable.core.infrastructure.lucene;

import com.searchable.core.domain.dictionary.DictionaryScope;
import com.searchable.core.domain.dictionary.UserDictionary;
import com.searchable.core.domain.dictionary.UserDictionaryEntry;
import com.searchable.core.domain.dictionary.UserDictionaryRepository;
import com.searchable.core.domain.dictionary.UserDictionaryResolver;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserDictionaryAnalyzerFactoryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static final class InMemoryRepo implements UserDictionaryRepository {
        private final Map<String, UserDictionary> store = new HashMap<>();
        @Override public void save(final UserDictionary d) { store.put(d.scope().key(), d); }
        @Override public Optional<UserDictionary> find(final DictionaryScope s) {
            return Optional.ofNullable(store.get(s.key()));
        }
        @Override public List<UserDictionary> findAll() { return new ArrayList<>(store.values()); }
        @Override public boolean delete(final DictionaryScope s) {
            return store.remove(s.key()) != null;
        }
    }

    private List<String> tokenize(final Analyzer analyzer, final String text) throws Exception {
        final List<String> tokens = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream("content", new StringReader(text))) {
            final CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                tokens.add(term.toString());
            }
            stream.end();
        }
        return tokens;
    }

    @Test
    void withoutDictionaryUsesDefaultAnalyzer() throws Exception {
        final UserDictionaryAnalyzerFactory factory = new UserDictionaryAnalyzerFactory(
            new UserDictionaryResolver(new InMemoryRepo()));
        try (Analyzer analyzer = factory.create("ns-1")) {
            assertThat(tokenize(analyzer, "全文検索エンジン")).isNotEmpty();
        }
    }

    @Test
    void globalEntryAffectsTokenization() throws Exception {
        final InMemoryRepo repo = new InMemoryRepo();
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global", List.of(
            new UserDictionaryEntry("カスタム単語あいうえお", "カスタム単語 あいうえお",
                "カスタムタンゴ アイウエオ", "カスタム名詞")), T0));

        final UserDictionaryAnalyzerFactory factory = new UserDictionaryAnalyzerFactory(
            new UserDictionaryResolver(repo));
        try (Analyzer analyzer = factory.create("ns-1")) {
            assertThat(tokenize(analyzer, "カスタム単語あいうえおは")).contains("カスタム単語");
        }
    }

    @Test
    void namespaceEntryAddsOnTopOfGlobal() throws Exception {
        final InMemoryRepo repo = new InMemoryRepo();
        repo.save(new UserDictionary(DictionaryScope.GLOBAL, "global", List.of(
            new UserDictionaryEntry("グローバルワード", "グローバル ワード",
                "グローバル ワード", "カスタム名詞")), T0));
        repo.save(new UserDictionary(DictionaryScope.namespace("ns-1"), "ns-1", List.of(
            new UserDictionaryEntry("ナマスペース単語", "ナマスペース 単語",
                "ナマスペース タンゴ", "カスタム名詞")), T0));

        final UserDictionaryAnalyzerFactory factory = new UserDictionaryAnalyzerFactory(
            new UserDictionaryResolver(repo));
        try (Analyzer analyzer = factory.create("ns-1")) {
            final List<String> tokens = tokenize(analyzer,
                "グローバルワードとナマスペース単語");
            assertThat(tokens).contains("グローバル", "ナマスペース");
        }
    }
}
