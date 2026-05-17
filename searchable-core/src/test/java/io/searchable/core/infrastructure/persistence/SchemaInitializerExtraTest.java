package io.searchable.core.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaInitializerExtraTest {

    @TempDir Path tempDir;

    @Test
    void constructorRejectsNullArgs() {
        assertThatThrownBy(() -> new SchemaInitializer(null))
            .isInstanceOf(NullPointerException.class);
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:schema-null;DB_CLOSE_DELAY=-1", "sa", ""));
        assertThatThrownBy(() -> new SchemaInitializer(ds, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingSchemaResource() {
        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:schema-missing;DB_CLOSE_DELAY=-1", "sa", ""));
        assertThatThrownBy(() -> new SchemaInitializer(ds, "/__no_such_schema__.sql").initialize())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Schema resource not found");
    }

    @Test
    void rejectsMalformedSchemaResource() throws Exception {
        // Create a custom schema file with a non-existent SQL statement.
        // We can't easily point SchemaInitializer at a file URL (it expects
        // a classpath resource), so instead let it run with an SQL command
        // that fails at runtime. To do that we write a test-only file and
        // ensure it's on the classpath via target/test-classes.
        final Path classes = Path.of("target/test-classes");
        Files.createDirectories(classes);
        final Path bad = classes.resolve("__bad_schema_test.sql");
        Files.writeString(bad, "INVALID SQL HERE;");

        final DataSource ds = DataSourceFactory.create(new PersistenceConfig(
            "H2", "jdbc:h2:mem:schema-bad;DB_CLOSE_DELAY=-1", "sa", ""));
        try {
            assertThatThrownBy(() -> new SchemaInitializer(ds, "/__bad_schema_test.sql").initialize())
                .isInstanceOf(IllegalStateException.class);
        } finally {
            Files.deleteIfExists(bad);
        }
    }
}
