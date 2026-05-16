package io.searchable.core.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs the bundled metadata DDL on the configured {@link DataSource}.
 *
 * <p>The DDL uses {@code CREATE TABLE IF NOT EXISTS}, so calling
 * {@link #initialize()} on an already-initialized database is a no-op.
 */
public final class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
    private static final String DEFAULT_SCHEMA_RESOURCE = "/schema.sql";

    private final DataSource dataSource;
    private final String schemaResource;

    public SchemaInitializer(final DataSource dataSource) {
        this(dataSource, DEFAULT_SCHEMA_RESOURCE);
    }

    public SchemaInitializer(final DataSource dataSource, final String schemaResource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.schemaResource = Objects.requireNonNull(schemaResource, "schemaResource must not be null");
    }

    public void initialize() {
        final List<String> statements = loadStatements();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (final String sql : statements) {
                    log.debug("executing DDL: {}", sql.replaceAll("\\s+", " "));
                    stmt.execute(sql);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new IllegalStateException("Failed to initialize schema", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize schema", e);
        }
        log.info("schema initialization complete ({} statements)", statements.size());
    }

    private List<String> loadStatements() {
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(schemaResource)) {
            if (in == null) {
                throw new IllegalStateException("Schema resource not found: " + schemaResource);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return splitStatements(reader);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + schemaResource, e);
        }
    }

    private static List<String> splitStatements(final BufferedReader reader) throws IOException {
        final List<String> statements = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                final String sql = current.substring(0, current.length() - 2).trim();
                if (!sql.isEmpty()) {
                    statements.add(sql);
                }
                current.setLength(0);
            }
        }
        final String trailing = current.toString().trim();
        if (!trailing.isEmpty()) {
            statements.add(trailing);
        }
        return statements;
    }
}
