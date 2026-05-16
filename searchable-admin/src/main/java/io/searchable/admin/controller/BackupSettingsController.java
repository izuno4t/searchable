package io.searchable.admin.controller;

import io.searchable.admin.config.SearchableProperties;
import io.searchable.core.application.BackupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Backup settings page (TASK-110).
 *
 * <p>Displays the configured backup directory and exposes a button that
 * triggers an immediate snapshot via {@link BackupService}. Scheduled
 * backups are run by a separate {@code BackupScheduler} the operator can
 * provision in the runtime configuration; this page links to the docs
 * for the cron setup.
 */
@Controller
@RequestMapping("/settings/backup")
public class BackupSettingsController {

    private final BackupService backupService;
    private final SearchableProperties properties;

    public BackupSettingsController(final BackupService backupService,
                                    final SearchableProperties properties) {
        this.backupService = backupService;
        this.properties = properties;
    }

    @GetMapping
    public String view(final Model model) {
        model.addAttribute("activeNav", "backup");
        model.addAttribute("pageTitle", "Backup settings");
        model.addAttribute("rootDirectory", defaultBackupDirectory());
        return "settings/backup";
    }

    @PostMapping("/run")
    public String runOnce(@RequestParam(value = "directory", required = false) final String directory,
                          final RedirectAttributes flash) {
        try {
            final Path target = resolveTarget(directory);
            Files.createDirectories(target.getParent());
            final var summary = backupService.snapshot(target);
            flash.addFlashAttribute("flashSuccess",
                "バックアップ完了: " + summary.namespaceIds().size()
                    + " namespace, " + summary.totalBytes() + " bytes -> " + target);
        } catch (Exception e) {
            flash.addFlashAttribute("flashError",
                "バックアップに失敗しました: " + e.getMessage());
        }
        return "redirect:/settings/backup";
    }

    private Path resolveTarget(final String directory) {
        final Path base = (directory == null || directory.isBlank())
            ? defaultBackupDirectory()
            : Path.of(directory);
        return base.resolve("snapshot-" + Instant.now().toString().replace(':', '-'));
    }

    private Path defaultBackupDirectory() {
        return properties.getDataDirectory().resolve("backups");
    }
}
