package com.searchable.core.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchable.core.domain.namespace.Namespace;
import com.searchable.core.domain.namespace.NamespaceConfig;
import com.searchable.core.domain.namespace.NamespaceRepository;

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
 * JDBC implementation of {@link NamespaceRepository} backed by the
 * {@code NAMESPACE} table.
 */
public final class JdbcNamespaceRepository implements NamespaceRepository {

    private static final String UPSERT_SQL = """
        MERGE INTO NAMESPACE
            (ID, NAME, CONFIG_JSON, CREATED_AT, UPDATED_AT)
        KEY (ID)
        VALUES (?, ?, ?, ?, ?)
        """;

    private static final String SELECT_BY_ID = """
        SELECT ID, NAME, CONFIG_JSON, CREATED_AT, UPDATED_AT
          FROM NAMESPACE
         WHERE ID = ?
        """;

    private static final String SELECT_ALL = """
        SELECT ID, NAME, CONFIG_JSON, CREATED_AT, UPDATED_AT
          FROM NAMESPACE
         ORDER BY ID
        """;

    private static final String DELETE_SQL = "DELETE FROM NAMESPACE WHERE ID = ?";

    private static final String EXISTS_SQL = "SELECT 1 FROM NAMESPACE WHERE ID = ?";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcNamespaceRepository(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = JsonMapper.instance();
    }

    @Override
    public void save(final Namespace namespace) {
        Objects.requireNonNull(namespace, "namespace must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, namespace.id());
            ps.setString(2, namespace.name());
            ps.setString(3, serializeConfig(namespace.config()));
            ps.setTimestamp(4, Timestamp.from(namespace.createdAt()));
            ps.setTimestamp(5, Timestamp.from(namespace.updatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save namespace " + namespace.id(), e);
        }
    }

    @Override
    public Optional<Namespace> findById(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load namespace " + id, e);
        }
    }

    @Override
    public List<Namespace> findAll() {
        final List<Namespace> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list namespaces", e);
        }
        return result;
    }

    @Override
    public boolean delete(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete namespace " + id, e);
        }
    }

    @Override
    public boolean exists(final String id) {
        Objects.requireNonNull(id, "id must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(EXISTS_SQL)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check namespace " + id, e);
        }
    }

    private Namespace map(final ResultSet rs) throws SQLException {
        final String id = rs.getString("ID");
        final String name = rs.getString("NAME");
        final NamespaceConfig config = deserializeConfig(rs.getString("CONFIG_JSON"));
        final Instant createdAt = rs.getTimestamp("CREATED_AT").toInstant();
        final Instant updatedAt = rs.getTimestamp("UPDATED_AT").toInstant();
        return new Namespace(id, name, config, createdAt, updatedAt);
    }

    private String serializeConfig(final NamespaceConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize NamespaceConfig", e);
        }
    }

    private NamespaceConfig deserializeConfig(final String json) {
        try {
            return objectMapper.readValue(json, NamespaceConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize NamespaceConfig", e);
        }
    }
}
