package io.searchable.core.domain.dictionary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDictionaryEntryTest {

    @Test
    void roundTripsViaCsv() {
        final UserDictionaryEntry entry = new UserDictionaryEntry(
            "関西国際空港", "関西 国際 空港", "カンサイ コクサイ クウコウ", "カスタム名詞");
        assertThat(UserDictionaryEntry.fromCsv(entry.toCsv())).isEqualTo(entry);
    }

    @Test
    void rejectsCommaInField() {
        assertThatThrownBy(() -> new UserDictionaryEntry("a,b", "x", "y", "z"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankField() {
        assertThatThrownBy(() -> new UserDictionaryEntry(" ", "x", "y", "z"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongFieldCount() {
        assertThatThrownBy(() -> UserDictionaryEntry.fromCsv("a,b,c"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
