package io.searchable.core.infrastructure.dictionary;

import io.searchable.core.domain.dictionary.DictionaryScope;
import io.searchable.core.domain.dictionary.UserDictionary;
import io.searchable.core.testing.H2TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUserDictionaryRepositoryExtraTest {

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

    @Test
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new JdbcUserDictionaryRepository(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void apiRejectsNullArgs() {
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.find(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.delete(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyEntriesPersistAsBlankCsv() {
        repository.save(new UserDictionary(
            DictionaryScope.GLOBAL, "empty", List.of(),
            Instant.parse("2026-01-01T00:00:00Z")));
        assertThat(repository.find(DictionaryScope.GLOBAL).orElseThrow().entries()).isEmpty();
    }

    @Test
    void parseEntriesIgnoresBlankAndCommentLines() throws Exception {
        // Insert a row whose CSV column contains commented and blank lines
        // so the parseEntries skip branches run.
        try (var c = db.dataSource().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "MERGE INTO USER_DICTIONARY (SCOPE_KEY, NAME, ENTRIES_CSV, UPDATED_AT) "
                 + "KEY (SCOPE_KEY) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, "GLOBAL");
            ps.setString(2, "with-comments");
            ps.setString(3, "# header comment\n\n語,ご,ゴ,名詞\n   \n");
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
        final UserDictionary loaded = repository.find(DictionaryScope.GLOBAL).orElseThrow();
        assertThat(loaded.entries()).hasSize(1);
        assertThat(loaded.entries().get(0).surface()).isEqualTo("語");
    }

    @Test
    void sqlErrorsWrappedAsIllegalState() throws Exception {
        try (var c = db.dataSource().getConnection(); var s = c.createStatement()) {
            s.execute("DROP TABLE USER_DICTIONARY");
        }
        final UserDictionary d = new UserDictionary(DictionaryScope.GLOBAL, "n", List.of(),
            Instant.parse("2026-01-01T00:00:00Z"));
        assertThatThrownBy(() -> repository.save(d)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.find(DictionaryScope.GLOBAL))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.findAll()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.delete(DictionaryScope.GLOBAL))
            .isInstanceOf(IllegalStateException.class);
    }
}
