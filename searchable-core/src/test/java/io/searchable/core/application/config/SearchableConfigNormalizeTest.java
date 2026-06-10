package io.searchable.core.application.config;

import io.searchable.core.infrastructure.lucene.StorageBackend;
import io.searchable.core.infrastructure.persistence.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolution rules for {@link ApplicationConfig#normalize(ApplicationConfig, Path)};
 * see {@code docs/devel/adr/0002-data-directory-relative-path-resolution.md}.
 */
class ApplicationConfigNormalizeTest {

    @TempDir Path tempDir;

    private ApplicationConfig raw(final Path dataDir, final PersistenceConfig p,
                                  final IndexConfig idx, final PluginsConfig pl) {
        return new ApplicationConfig(dataDir, p, idx, pl, GlobalConfig.defaults());
    }

    @Test
    void absoluteDataDirectoryStaysAsIs() {
        final Path abs = tempDir.resolve("absolute-data").toAbsolutePath();
        final ApplicationConfig in = raw(abs,
            new PersistenceConfig("H2", "jdbc:h2:mem:test", "sa", ""),
            new IndexConfig(abs.resolve("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.dataDirectory()).isEqualTo(abs);
    }

    @Test
    void relativeDataDirectoryResolvesAgainstBase() {
        final ApplicationConfig in = raw(Path.of("./mydata"),
            new PersistenceConfig("H2", "jdbc:h2:mem:test", "sa", ""),
            new IndexConfig(Path.of("./mydata/idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.dataDirectory())
            .isEqualTo(tempDir.toAbsolutePath().normalize().resolve("mydata"));
    }

    @Test
    void unsetIndexDirectoryDefaultsToDataDirectoryIndexes() {
        // IndexConfig.defaults() is what the ApplicationConfig record constructor
        // installs when YAML omits the `index` key. Normalize must replace that
        // sentinel with <data-directory>/indexes.
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", "jdbc:h2:mem:test", "sa", ""),
            IndexConfig.defaults(),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.index().directory())
            .isEqualTo(out.dataDirectory().resolve("indexes"));
    }

    @Test
    void relativeIndexDirectoryResolvesAgainstDataDirectory() {
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", "jdbc:h2:mem:test", "sa", ""),
            new IndexConfig(Path.of("custom-idx"), StorageBackend.MEMORY),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.index().directory())
            .isEqualTo(out.dataDirectory().resolve("custom-idx"));
        assertThat(out.index().backend()).isEqualTo(StorageBackend.MEMORY);
    }

    @Test
    void relativePluginsDirectoryResolvesAgainstDataDirectory() {
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", "jdbc:h2:mem:test", "sa", ""),
            new IndexConfig(Path.of("idx")),
            new PluginsConfig(Path.of("../plugins")));

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.plugins().directory())
            .isEqualTo(out.dataDirectory().resolve("../plugins").normalize());
    }

    @Test
    void classpathOnlyPluginsStayAsIs() {
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", "jdbc:h2:mem:test", "sa", ""),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.plugins().directory()).isNull();
    }

    @Test
    void h2EmbeddedUrlRelativePathIsRewrittenAndAutoServerAdded() {
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", "jdbc:h2:./data/metadata;MODE=PostgreSQL", "sa", ""),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        final Path expected = out.dataDirectory().resolve("data/metadata");
        assertThat(out.persistence().url())
            .isEqualTo("jdbc:h2:" + expected + ";MODE=PostgreSQL;AUTO_SERVER=TRUE");
    }

    @Test
    void h2EmbeddedUrlAbsolutePathPreservedButAutoServerAdded() {
        final String url = "jdbc:h2:/var/lib/searchable/metadata";
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", url, "sa", ""),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.persistence().url())
            .isEqualTo("jdbc:h2:/var/lib/searchable/metadata;AUTO_SERVER=TRUE");
    }

    @Test
    void h2MemoryUrlLeftAlone() {
        final String url = "jdbc:h2:mem:transient";
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", url, "sa", ""),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.persistence().url()).isEqualTo(url);
    }

    @Test
    void h2TcpUrlLeftAlone() {
        final String url = "jdbc:h2:tcp://db.local:9092/searchable";
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", url, "sa", ""),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.persistence().url()).isEqualTo(url);
    }

    @Test
    void h2TildePathIsPreservedButAutoServerAdded() {
        // H2 has its own tilde-expansion logic; intercepting it locally would
        // confuse users. The path part stays untouched, but AUTO_SERVER=TRUE
        // is still appended so the CLI can co-write while an app is reading.
        final String url = "jdbc:h2:~/searchable/metadata";
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", url, "sa", ""),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.persistence().url())
            .isEqualTo("jdbc:h2:~/searchable/metadata;AUTO_SERVER=TRUE");
    }

    @Test
    void nonH2UrlLeftAlone() {
        final String url = "jdbc:postgresql://db.example.com:5432/searchable";
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("POSTGRESQL", url, "user", "pw"),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        assertThat(out.persistence().url()).isEqualTo(url);
    }

    @Test
    void h2FilePrefixedUrlIsRewrittenAndAutoServerAdded() {
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", "jdbc:h2:file:./db;DB_CLOSE_DELAY=-1", "sa", ""),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        final Path expected = out.dataDirectory().resolve("db");
        assertThat(out.persistence().url())
            .isEqualTo("jdbc:h2:file:" + expected + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE");
    }

    @Test
    void h2UrlAlreadyContainingAutoServerIsNotDoubled() {
        final String url = "jdbc:h2:./db;AUTO_SERVER=TRUE;MODE=PostgreSQL";
        final ApplicationConfig in = raw(Path.of("./data"),
            new PersistenceConfig("H2", url, "sa", ""),
            new IndexConfig(Path.of("idx")),
            PluginsConfig.classpathOnly());

        final ApplicationConfig out = ApplicationConfig.normalize(in, tempDir);

        final Path expected = out.dataDirectory().resolve("db");
        assertThat(out.persistence().url())
            .isEqualTo("jdbc:h2:" + expected + ";AUTO_SERVER=TRUE;MODE=PostgreSQL");
    }
}
