package io.searchable.core.domain.dictionary;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDictionaryTest {

    @Test
    void emptyFactoryProducesScopeKeyName() {
        final UserDictionary dict = UserDictionary.empty(DictionaryScope.GLOBAL, Instant.now());
        assertThat(dict.name()).isEqualTo("GLOBAL");
        assertThat(dict.entries()).isEmpty();
    }

    @Test
    void rejectsNullScopeOrFields() {
        final Instant now = Instant.now();
        assertThatThrownBy(() -> new UserDictionary(null, "n", List.of(), now))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserDictionary(DictionaryScope.GLOBAL, null, List.of(), now))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserDictionary(DictionaryScope.GLOBAL, "n", null, now))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserDictionary(DictionaryScope.GLOBAL, "n", List.of(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() ->
            new UserDictionary(DictionaryScope.GLOBAL, " ", List.of(), Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entriesAreDefensivelyCopied() {
        final List<UserDictionaryEntry> mutable = new ArrayList<>();
        mutable.add(new UserDictionaryEntry("読み", "読 み", "ヨミ", "名詞"));
        final UserDictionary d = new UserDictionary(
            DictionaryScope.GLOBAL, "n", mutable, Instant.now());

        mutable.add(new UserDictionaryEntry("追加", "追 加", "ツイカ", "名詞"));

        assertThat(d.entries()).hasSize(1);
    }
}
