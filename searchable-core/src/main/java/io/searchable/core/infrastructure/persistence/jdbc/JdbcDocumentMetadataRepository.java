package io.searchable.core.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.core.domain.document.DocumentMetadataRecord;
import io.searchable.core.domain.document.DocumentMetadataRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed {@link DocumentMetadataRepository}. Stores document-level
 * metadata in the {@code DOCUMENT_METADATA} table.
 */
public final class JdbcDocumentMetadataRepository implements DocumentMetadataRepository {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE =
        new TypeReference<>() { };

    private static final String UPSERT_SQL = """
        MERGE INTO DOCUMENT_METADATA
            (NAMESPACE_ID, DOCUMENT_ID, TITLE, METADATA_JSON, INDEXED_AT)
        KEY (NAMESPACE_ID, DOCUMENT_ID)
        VALUES (?, ?, ?, ?, ?)
        """;

    private static final String SELECT_BY_ID = """
        SELECT NAMESPACE_ID, DOCUMENT_ID, TITLE, METADATA_JSON, INDEXED_AT
          FROM DOCUMENT_METADATA
         WHERE NAMESPACE_ID = ? AND DOCUMENT_ID = ?
        """;

    private static final String LIST_SQL = """
        SELECT NAMESPACE_ID, DOCUMENT_ID, TITLE, METADATA_JSON, INDEXED_AT
          FROM DOCUMENT_METADATA
         WHERE NAMESPACE_ID = ?
         ORDER BY INDEXED_AT DESC, DOCUMENT_ID
         LIMIT ? OFFSET ?
        """;

    private static final String COUNT_SQL =
        "SELECT COUNT(*) FROM DOCUMENT_METADATA WHERE NAMESPACE_ID = ?";

    private static final String DELETE_SQL =
        "DELETE FROM DOCUMENT_METADATA WHERE NAMESPACE_ID = ? AND DOCUMENT_ID = ?";

    private static final String DELETE_BY_NAMESPACE_SQL =
        "DELETE FROM DOCUMENT_METADATA WHERE NAMESPACE_ID = ?";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcDocumentMetadataRepository(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = JsonMapper.instance();
    }

    @Override
    public void save(final DocumentMetadataRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, record.namespaceId());
            ps.setString(2, record.documentId());
            ps.setString(3, record.title());
            ps.setString(4, serializeMetadata(record.metadata()));
            ps.setTimestamp(5, Timestamp.from(record.indexedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to save document metadata for "
                    + record.namespaceId() + "/" + record.documentId(), e);
        }
    }

    @Override
    public Optional<DocumentMetadataRecord> findById(final String namespaceId,
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
                "Failed to load document metadata for "
                    + namespaceId + "/" + documentId, e);
        }
    }

    @Override
    public List<DocumentMetadataRecord> findByIds(final String namespaceId,
                                                  final Collection<String> documentIds) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(documentIds, "documentIds must not be null");
        // Deduplicate while preserving order; an empty input avoids a
        // pointless query.
        final List<String> ids = new ArrayList<>(new LinkedHashSet<>(documentIds));
        if (ids.isEmpty()) {
            return List.of();
        }
        final String placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        final String sql = """
            SELECT NAMESPACE_ID, DOCUMENT_ID, TITLE, METADATA_JSON, INDEXED_AT
              FROM DOCUMENT_METADATA
             WHERE NAMESPACE_ID = ? AND DOCUMENT_ID IN (""" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespaceId);
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(i + 2, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                final List<DocumentMetadataRecord> results = new ArrayList<>(ids.size());
                while (rs.next()) {
                    results.add(map(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to batch-load document metadata for namespace " + namespaceId, e);
        }
    }

    @Override
    public List<DocumentMetadataRecord> list(final String namespaceId,
                                             final int offset,
                                             final int limit) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LIST_SQL)) {
            ps.setString(1, namespaceId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                final List<DocumentMetadataRecord> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(map(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to list document metadata for namespace " + namespaceId, e);
        }
    }

    @Override
    public long count(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_SQL)) {
            ps.setString(1, namespaceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to count document metadata for namespace " + namespaceId, e);
        }
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
                "Failed to delete document metadata for "
                    + namespaceId + "/" + documentId, e);
        }
    }

    @Override
    public void deleteByNamespace(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_NAMESPACE_SQL)) {
            ps.setString(1, namespaceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to clear document metadata for namespace " + namespaceId, e);
        }
    }

    private DocumentMetadataRecord map(final ResultSet rs) throws SQLException {
        final String namespaceId = rs.getString("NAMESPACE_ID");
        final String documentId = rs.getString("DOCUMENT_ID");
        final String title = rs.getString("TITLE");
        final Map<String, Object> metadata = deserializeMetadata(rs.getString("METADATA_JSON"));
        final Timestamp indexedAt = rs.getTimestamp("INDEXED_AT");
        return new DocumentMetadataRecord(namespaceId, documentId, title, metadata,
            indexedAt.toInstant());
    }

    private String serializeMetadata(final Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize document metadata", e);
        }
    }

    private Map<String, Object> deserializeMetadata(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, METADATA_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize document metadata", e);
        }
    }
}
