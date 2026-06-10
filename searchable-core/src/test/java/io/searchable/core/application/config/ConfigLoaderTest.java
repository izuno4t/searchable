package io.searchable.core.application.config;

import io.searchable.core.domain.search.SearchOrder;
import io.searchable.core.domain.search.SearchStrategy;
import io.searchable.core.domain.search.SearchType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    @Test
    void loadsFullConfigFromYamlClasspath() {
        final ConfigLoader loader = new ConfigLoader();
        final SearchableConfig config;
        try (InputStream in = getClass().getResourceAsStream("/searchable-test-config.yaml")) {
            assertThat(in).isNotNull();
            config = loader.load(in);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        assertThat(config.dataDirectory()).isEqualTo(Path.of("./data"));
        assertThat(config.persistence().type()).isEqualTo("H2");
        assertThat(config.persistence().url()).contains("jdbc:h2:");
        assertThat(config.index().directory()).isEqualTo(Path.of("./data/indexes"));
        assertThat(config.plugins().directory()).isEqualTo(Path.of("./plugins"));
        assertThat(config.global().defaultArchitecture()).isEqualTo(SearchType.HYBRID);
        assertThat(config.global().defaultSearchStrategy()).isEqualTo(SearchStrategy.PARALLEL);
        assertThat(config.global().defaultSearchOrder()).isEqualTo(SearchOrder.FULL_TEXT_FIRST);
    }

    @Test
    void rejectsMissingRequiredField() {
        final String yaml = "persistence:\n  type: H2\n  url: \"jdbc:h2:./data/x\"\n  username: sa\n";
        try (InputStream in = new java.io.ByteArrayInputStream(yaml.getBytes())) {
            assertThatThrownBy(() -> new ConfigLoader().load(in))
                .isInstanceOf(IllegalStateException.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void loadsPostgresqlConfigFromYamlClasspath() {
        final ConfigLoader loader = new ConfigLoader();
        final SearchableConfig config;
        try (InputStream in = getClass().getResourceAsStream("/searchable-postgres-config.yaml")) {
            assertThat(in).isNotNull();
            config = loader.load(in);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        assertThat(config.persistence().type()).isEqualTo("POSTGRESQL");
        assertThat(config.persistence().url()).startsWith("jdbc:postgresql://");
        assertThat(config.persistence().username()).isEqualTo("searchable");
        assertThat(config.persistence().password()).isEqualTo("secret");
        assertThat(config.persistence().maxPoolSize()).isEqualTo(32);
    }

    @Test
    void appliesDefaultsForOmittedSections() {
        final String yaml = """
            data-directory: ./data
            persistence:
              type: H2
              url: "jdbc:h2:./x"
              username: sa
              password: ""
            """;
        final SearchableConfig cfg = new ConfigLoader()
            .load(new java.io.ByteArrayInputStream(yaml.getBytes()));

        assertThat(cfg.index().directory()).isEqualTo(Path.of("./data/indexes"));
        assertThat(cfg.plugins().directory()).isNull();
        assertThat(cfg.global().defaultArchitecture()).isEqualTo(SearchType.FULL_TEXT);
    }
}
