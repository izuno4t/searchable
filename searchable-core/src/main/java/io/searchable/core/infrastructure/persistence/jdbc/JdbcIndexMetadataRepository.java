package io.searchable.core.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.searchable.core.domain.index.IndexMetadata;
import io.searchable.core.domain.index.IndexMetadataRepository;
import io.searchable.core.domain.index.IndexStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link IndexMetadataRepository} backed by the
 * {@code INDEX_METADATA} table.
 */
public final class JdbcIndexMetadataRepository implements IndexMetadataRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private static final String UPSERT_SQL = """
        MERGE INTO INDEX_METADATA
            (NAMESPACE_ID, DOCUMENT_COUNT, INDEX_SIZE_BYTES, STATUS,
             LAST_UPDATED, STATISTICS_JSON)
        KEY (NAMESPACE_ID)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_BY_ID = """
        SELECT NAMESPACE_ID, DOCUMENT_COUNT, INDEX_SIZE_BYTES, STATUS,
               LAST_UPDATED, STATISTICS_JSON
          FROM INDEX_METADATA
         WHERE NAMESPACE_ID = ?
        """;

    private static final String SELECT_ALL = """
        SELECT NAMESPACE_ID, DOCUMENT_COUNT, INDEX_SIZE_BYTES, STATUS,
               LAST_UPDATED, STATISTICS_JSON
          FROM INDEX_METADATA
         ORDER BY NAMESPACE_ID
        """;

    private static final String DELETE_SQL = "DELETE FROM INDEX_METADATA WHERE NAMESPACE_ID = ?";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcIndexMetadataRepository(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = JsonMapper.instance();
    }

    @Override
    public void save(final IndexMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, metadata.namespaceId());
            ps.setLong(2, metadata.documentCount());
            ps.setLong(3, metadata.indexSizeBytes());
            ps.setString(4, metadata.status().name());
            ps.setTimestamp(5, Timestamp.from(metadata.lastUpdated()));
            ps.setString(6, serializeStatistics(metadata.statistics()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to save index metadata for " + metadata.namespaceId(), e);
        }
    }

    @Override
    public Optional<IndexMetadata> findByNamespaceId(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setString(1, namespaceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to load index metadata for " + namespaceId, e);
        }
    }

    @Override
    public List<IndexMetadata> findAll() {
        final List<IndexMetadata> all = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                all.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list index metadata", e);
        }
        return all;
    }

    @Override
    public boolean delete(final String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, namespaceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Failed to delete index metadata for " + namespaceId, e);
        }
    }

    private IndexMetadata map(final ResultSet rs) throws SQLException {
        final String namespaceId = rs.getString("NAMESPACE_ID");
        final long count = rs.getLong("DOCUMENT_COUNT");
        final long size = rs.getLong("INDEX_SIZE_BYTES");
        final IndexStatus status = IndexStatus.valueOf(rs.getString("STATUS"));
        final Instant lastUpdated = rs.getTimestamp("LAST_UPDATED").toInstant();
        final Map<String, Object> stats = deserializeStatistics(rs.getString("STATISTICS_JSON"));
        return new IndexMetadata(namespaceId, count, size, lastUpdated, status, stats);
    }

    private String serializeStatistics(final Map<String, Object> stats) {
        if (stats == null || stats.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(stats);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize statistics", e);
        }
    }

    private Map<String, Object> deserializeStatistics(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize statistics", e);
        }
    }
}
