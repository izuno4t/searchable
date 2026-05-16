package io.searchable.core.infrastructure.persistence.jdbc;

import io.searchable.core.domain.document.DocumentSource;
import io.searchable.core.domain.document.DocumentSourceRepository;

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
 * JDBC-backed {@link DocumentSourceRepository} keyed by {@code (NAMESPACE_ID,
 * DOCUMENT_ID)} in the {@code DOCUMENT_SOURCE} table.
 */
public final class JdbcDocumentSourceRepository implements DocumentSourceRepository {

    private static final String UPSERT_SQL = """
        MERGE INTO DOCUMENT_SOURCE
            (NAMESPACE_ID, DOCUMENT_ID, SOURCE_TYPE, SOURCE_LOCATION,
             CONTENT_HASH, SOURCE_UPDATED, INDEXED_AT)
        KEY (NAMESPACE_ID, DOCUMENT_ID)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_BY_ID = """
        SELECT SOURCE_TYPE, SOURCE_LOCATION, CONTENT_HASH, SOURCE_UPDATED
          FROM DOCUMENT_SOURCE
         WHERE NAMESPACE_ID = ? AND DOCUMENT_ID = ?
        """;

    private static final String SELECT_BY_NAMESPACE = """
        SELECT SOURCE_TYPE, SOURCE_LOCATION, CONTENT_HASH, SOURCE_UPDATED
          FROM DOCUMENT_SOURCE
         WHERE NAMESPACE_ID = ?
         ORDER BY DOCUMENT_ID
        """;

    private static final String DELETE_SQL =
        "DELETE FROM DOCUMENT_SOURCE WHERE NAMESPACE_ID = ? AND DOCUMENT_ID = ?";

    private final DataSource dataSource;

    public JdbcDocumentSourceRepository(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void save(final String namespaceId, final String documentId,
                     final DocumentSource source) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, namespaceId);
            ps.setString(2, documentId);
            ps.setString(3, source.type());
            ps.setString(4, source.location());
            if (source.contentHash() == null) {
                ps.setNull(5, java.sql.Types.VARCHAR);
            } else {
                ps.setString(5, source.contentHash());
            }
            if (source.sourceUpdated() == null) {
                ps.setNull(6, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(6, Timestamp.from(source.sourceUpdated()));
            }
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to save document source for " + namespaceId + "/" + documentId, e);
        }
    }

    @Override
    public Optional<DocumentSource> findByDocumentId(final String namespaceId,
                                                     final String documentId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setString(1, namespaceId);
            ps.setString(2, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to load document source for " + namespaceId + "/" + documentId, e);
        }
    }

    @Override
    public List<DocumentSource> findByNamespace(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        final List<DocumentSource> all = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_NAMESPACE)) {
            ps.setString(1, namespaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    all.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to list document sources for " + namespaceId, e);
        }
        return all;
    }

    @Override
    public boolean delete(final String namespaceId, final String documentId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, namespaceId);
            ps.setString(2, documentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to delete document source for " + namespaceId + "/" + documentId, e);
        }
    }

    private DocumentSource map(final ResultSet rs) throws SQLException {
        final String type = rs.getString("SOURCE_TYPE");
        final String location = rs.getString("SOURCE_LOCATION");
        final String hash = rs.getString("CONTENT_HASH");
        final Timestamp updated = rs.getTimestamp("SOURCE_UPDATED");
        return new DocumentSource(type, location, hash,
            updated == null ? null : updated.toInstant());
    }
}
