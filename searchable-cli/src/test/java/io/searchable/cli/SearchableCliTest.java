package io.searchable.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SearchableCliTest {

    @TempDir Path tempDir;

    @Test
    void rootCommandPrintsHelp() {
        final StringWriter out = new StringWriter();
        final CommandLine cmd = new CommandLine(new SearchableCli())
            .setOut(new PrintWriter(out));
        final int code = cmd.execute("--help");
        assertThat(code).isZero();
        assertThat(out.toString()).contains("searchable").contains("ingest").contains("status");
    }

    @Test
    void versionCommandPrintsVersionString() {
        final StringWriter out = new StringWriter();
        final CommandLine cmd = new CommandLine(new SearchableCli())
            .setOut(new PrintWriter(out));
        cmd.execute("--version");
        assertThat(out.toString()).contains("searchable-cli");
    }

    @Test
    void validateConfigSucceedsForValidYaml() throws Exception {
        final Path config = tempDir.resolve("config.yaml");
        Files.writeString(config, """
            data-directory: %s
            persistence:
              type: H2
              url: "jdbc:h2:mem:cli-test;DB_CLOSE_DELAY=-1"
              username: sa
              password: ""
            index:
              directory: %s
            """.formatted(tempDir, tempDir.resolve("indexes")));

        final CommandLine cmd = new CommandLine(new SearchableCli());
        final int code = cmd.execute("validate-config", "--config", config.toString());
        assertThat(code).isZero();
    }

    @Test
    void validateConfigReportsErrorForMissingFile() {
        final StringWriter err = new StringWriter();
        final CommandLine cmd = new CommandLine(new SearchableCli())
            .setErr(new PrintWriter(err));
        final int code = cmd.execute("validate-config",
            "--config", tempDir.resolve("missing.yaml").toString());
        assertThat(code).isEqualTo(2);
    }
}
