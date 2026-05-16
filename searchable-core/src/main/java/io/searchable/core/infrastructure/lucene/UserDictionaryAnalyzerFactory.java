package io.searchable.core.infrastructure.lucene;

import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import io.searchable.core.domain.dictionary.UserDictionaryResolver;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ja.dict.UserDictionary;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;

/**
 * {@link AnalyzerFactory} that consults a {@link UserDictionaryResolver}
 * and builds a {@link JapaneseAnalyzer} with the resolved user dictionary
 * applied. Falls back to the default analyzer when no entries are configured.
 */
public final class UserDictionaryAnalyzerFactory implements AnalyzerFactory {

    private final UserDictionaryResolver resolver;

    public UserDictionaryAnalyzerFactory(final UserDictionaryResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver);
    }

    @Override
    public Analyzer create(final String namespaceId) {
        final List<UserDictionaryEntry> entries = resolver.resolveFor(namespaceId);
        if (entries.isEmpty()) {
            return new JapaneseAnalyzer();
        }
        final UserDictionary userDictionary = buildUserDictionary(entries);
        return new JapaneseAnalyzer(
            userDictionary,
            JapaneseTokenizer.Mode.SEARCH,
            JapaneseAnalyzer.getDefaultStopSet(),
            JapaneseAnalyzer.getDefaultStopTags()
        );
    }

    private static UserDictionary buildUserDictionary(final List<UserDictionaryEntry> entries) {
        final StringBuilder csv = new StringBuilder();
        for (final UserDictionaryEntry entry : entries) {
            csv.append(entry.toCsv()).append('\n');
        }
        try (StringReader reader = new StringReader(csv.toString())) {
            return UserDictionary.open(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build Kuromoji user dictionary", e);
        }
    }
}
