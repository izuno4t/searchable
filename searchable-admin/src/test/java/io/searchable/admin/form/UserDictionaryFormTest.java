package io.searchable.admin.form;

import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.domain.dictionary.UserDictionaryEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDictionaryFormTest {

    private final UserDictionaryEntry e1 =
        new UserDictionaryEntry("Lucene", "Lucene", "ルセン", "固有名詞");
    private final UserDictionaryEntry e2 =
        new UserDictionaryEntry("形態素", "形態 素", "ケイタイ ソ", "名詞");

    @Test
    void fromCopiesNameAndSerializesEntries() {
        final UserDictionaryForm form = UserDictionaryForm.from(new UserDictionary(
            DictionaryScope.GLOBAL, "global", List.of(e1, e2),
            Instant.parse("2026-01-01T00:00:00Z")));

        assertThat(form.getName()).isEqualTo("global");
        assertThat(form.getEntriesCsv()).contains("Lucene").contains("形態素");
    }

    @Test
    void setEntriesCsvNormalisesNullToEmptyString() {
        final UserDictionaryForm form = new UserDictionaryForm();
        form.setEntriesCsv(null);
        assertThat(form.getEntriesCsv()).isEmpty();
    }

    @Test
    void parseEntriesIgnoresBlankAndCommentLines() {
        final UserDictionaryForm form = new UserDictionaryForm();
        form.setName("n");
        form.setEntriesCsv("""
            # header
            Lucene,Lucene,ルセン,固有名詞

            形態素,形態 素,ケイタイ ソ,名詞
            """);
        assertThat(form.parseEntries()).hasSize(2);
    }

    @Test
    void parseEntriesRejectsDuplicateSurface() {
        final UserDictionaryForm form = new UserDictionaryForm();
        form.setName("n");
        form.setEntriesCsv("""
            Lucene,Lucene,ルセン,固有名詞
            Lucene,Lucene,ルセン,固有名詞
            """);
        assertThatThrownBy(form::parseEntries)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate surface");
    }

    @Test
    void parseEntriesWrapsLineErrorWithLineNumber() {
        final UserDictionaryForm form = new UserDictionaryForm();
        form.setName("n");
        form.setEntriesCsv("Lucene,Lucene,ルセン,固有名詞\nbad,row,with,extra,column");
        assertThatThrownBy(form::parseEntries)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("line 2");
    }
}
