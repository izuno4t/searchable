package com.searchable.core.infrastructure.dictionary;

import com.searchable.core.domain.dictionary.DictionaryScope;
import com.searchable.core.domain.dictionary.UserDictionary;
import com.searchable.core.domain.dictionary.UserDictionaryEntry;
import com.searchable.core.domain.dictionary.UserDictionaryRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed {@link UserDictionaryRepository}.
 *
 * <p>Stores the entries as one CSV string per row keyed by {@link DictionaryScope#key()}.
 */
public final class JdbcUserDictionaryRepository implements UserDictionaryRepository {

    private static final String UPSERT_SQL = """
        MERGE INTO USER_DICTIONARY (SCOPE_KEY, NAME, ENTRIES_CSV, UPDATED_AT)
        KEY (SCOPE_KEY)
        VALUES (?, ?, ?, ?)
        """;

    private static final String SELECT_BY_KEY = """
        SELECT SCOPE_KEY, NAME, ENTRIES_CSV, UPDATED_AT
          FROM USER_DICTIONARY
         WHERE SCOPE_KEY = ?
        """;

    private static final String SELECT_ALL = """
        SELECT SCOPE_KEY, NAME, ENTRIES_CSV, UPDATED_AT
          FROM USER_DICTIONARY
         ORDER BY SCOPE_KEY
        """;

    private static final String DELETE_SQL = "DELETE FROM USER_DICTIONARY WHERE SCOPE_KEY = ?";

    private final DataSource dataSource;

    public JdbcUserDictionaryRepository(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void save(final UserDictionary dictionary) {
        Objects.requireNonNull(dictionary, "dictionary must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, dictionary.scope().key());
            ps.setString(2, dictionary.name());
            ps.setString(3, joinEntries(dictionary.entries()));
            ps.setTimestamp(4, Timestamp.from(dictionary.updatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to save user dictionary " + dictionary.scope(), e);
        }
    }

    @Override
    public Optional<UserDictionary> find(final DictionaryScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_KEY)) {
            ps.setString(1, scope.key());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to load user dictionary " + scope, e);
        }
    }

    @Override
    public List<UserDictionary> findAll() {
        final List<UserDictionary> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list user dictionaries", e);
        }
        return result;
    }

    @Override
    public boolean delete(final DictionaryScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, scope.key());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to delete user dictionary " + scope, e);
        }
    }

    private UserDictionary map(final ResultSet rs) throws SQLException {
        final DictionaryScope scope = DictionaryScope.fromKey(rs.getString("SCOPE_KEY"));
        final String name = rs.getString("NAME");
        final List<UserDictionaryEntry> entries = parseEntries(rs.getString("ENTRIES_CSV"));
        final Instant updatedAt = rs.getTimestamp("UPDATED_AT").toInstant();
        return new UserDictionary(scope, name, entries, updatedAt);
    }

    private String joinEntries(final List<UserDictionaryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final UserDictionaryEntry entry : entries) {
            sb.append(entry.toCsv()).append('\n');
        }
        return sb.toString();
    }

    private List<UserDictionaryEntry> parseEntries(final String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        final List<UserDictionaryEntry> entries = new ArrayList<>();
        for (final String line : csv.split("\\R")) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            entries.add(UserDictionaryEntry.fromCsv(trimmed));
        }
        return entries;
    }
}
