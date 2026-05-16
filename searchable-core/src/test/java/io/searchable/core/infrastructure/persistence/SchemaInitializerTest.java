package io.searchable.core.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaInitializerTest {

    @TempDir Path tempDir;

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        final String url = "jdbc:h2:" + tempDir.resolve("test") + ";MODE=PostgreSQL";
        dataSource = DataSourceFactory.create(new PersistenceConfig("H2", url, "sa", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
    }

    @Test
    void createsExpectedTables() throws Exception {
        new SchemaInitializer(dataSource).initialize();

        final Set<String> tables = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, "PUBLIC", "%",
                 new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toUpperCase());
            }
        }

        assertThat(tables).contains("NAMESPACE", "INDEX_METADATA", "DOCUMENT_SOURCE");
    }

    @Test
    void initializingTwiceIsIdempotent() {
        final SchemaInitializer init = new SchemaInitializer(dataSource);
        init.initialize();
        init.initialize();
        // No exception is the assertion.
    }
}
