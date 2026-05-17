package io.searchable.admin;

import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SearchableSpringBootTest
@TestPropertySource(properties = {
    "searchable.data-directory=./build/ui-backup-test",
    "searchable.persistence.url=jdbc:h2:mem:ui-backup-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "searchable.index.directory=./build/ui-backup-test/indexes"
})
class BackupSettingsControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void backupSettingsPageRendersDefaultRoot() throws Exception {
        mvc.perform(get("/settings/backup"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Backup settings")))
            .andExpect(content().string(containsString("backups")));
    }

    @Test
    void runOnceWithDefaultDirectoryCreatesSnapshot() throws Exception {
        final Path defaultRoot = Path.of("./build/ui-backup-test/backups").toAbsolutePath().normalize();
        Files.createDirectories(defaultRoot);

        mvc.perform(post("/settings/backup/run"))
            .andExpect(redirectedUrl("/settings/backup"))
            .andExpect(flash().attributeExists("flashSuccess"));

        try (var stream = Files.list(defaultRoot)) {
            assertThat(stream.anyMatch(p -> p.getFileName().toString().startsWith("snapshot-")))
                .isTrue();
        }
    }

    @Test
    void runOnceWithCustomDirectoryUsesParameter(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws Exception {
        final Path target = tmp.resolve("custom-backups");

        mvc.perform(post("/settings/backup/run")
                .param("directory", target.toString()))
            .andExpect(redirectedUrl("/settings/backup"))
            .andExpect(flash().attributeExists("flashSuccess"));

        try (Stream<Path> stream = Files.list(target)) {
            assertThat(stream.anyMatch(p -> p.getFileName().toString().startsWith("snapshot-")))
                .isTrue();
        }
    }

    @Test
    void runOnceWithUnwritableDirectoryReportsError() throws Exception {
        // "/" is not writable for the test user -> resolveTarget produces
        // /<timestamp> which Files.createDirectories cannot make. The
        // controller catches the exception and surfaces it via flashError.
        mvc.perform(post("/settings/backup/run")
                .param("directory", "/__searchable_no_such_path__"))
            .andExpect(redirectedUrl("/settings/backup"))
            .andExpect(flash().attributeExists("flashError"));
    }
}
