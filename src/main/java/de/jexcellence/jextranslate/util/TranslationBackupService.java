package de.jexcellence.jextranslate.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility responsible for creating timestamped backups of translation files and pruning stale copies
 * after repository synchronisation. Backups are stored under {@code translations/backups} by default
 * with per-file retention to prevent unbounded growth.
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.0
 */
public final class TranslationBackupService {

    private static final Logger LOGGER = TranslationLogger.getLogger(TranslationBackupService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int DEFAULT_RETENTION = 10;

    private final Path backupRoot;
    private final int retention;

    /**
     * Creates a backup service rooted at the provided translations directory using the default retention.
     *
     * @param translationsDirectory base translations directory
     */
    public TranslationBackupService(@NotNull final Path translationsDirectory) {
        this(translationsDirectory, DEFAULT_RETENTION);
    }

    /**
     * Creates a backup service rooted at the provided translations directory.
     *
     * @param translationsDirectory base translations directory
     * @param retention             maximum number of backups to retain per file
     */
    public TranslationBackupService(@NotNull final Path translationsDirectory, final int retention) {
        Objects.requireNonNull(translationsDirectory, "Translations directory cannot be null");
        this.backupRoot = translationsDirectory.resolve("backups");
        this.retention = Math.max(retention, 1);
    }

    /**
     * Creates a backup for the provided file when it exists, copying attributes and emitting structured
     * diagnostics. When the file does not exist an empty optional is returned.
     *
     * @param source the file to back up
     * @param reason textual description of why the backup was generated
     * @return optional path to the created backup
     * @throws IOException when the backup could not be created
     */
    @NotNull
    public Optional<Path> createBackup(@NotNull final Path source, @NotNull final String reason) throws IOException {
        Objects.requireNonNull(source, "Source cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");

        if (!Files.exists(source) || Files.isDirectory(source)) {
            return Optional.empty();
        }

        Files.createDirectories(this.backupRoot);

        var baseName = source.getFileName().toString();
        var timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        var backupFile = this.backupRoot.resolve(baseName + "." + timestamp + ".bak");

        Files.copy(source, backupFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        LOGGER.info(() -> TranslationLogger.message("Created backup", Map.of("source", source.toString(), "backup", backupFile.toString(), "reason", reason)));

        pruneBackups(baseName);

        return Optional.of(backupFile);
    }

    private void pruneBackups(@NotNull final String baseName) {
        try (var stream = Files.newDirectoryStream(this.backupRoot, baseName + ".*.bak")) {
            var backups = new ArrayList<Path>();
            stream.forEach(backups::add);
            backups.sort(Comparator.comparing(Path::getFileName).reversed());

            backups.stream().skip(this.retention).forEach(obsolete -> {
                try {
                    Files.deleteIfExists(obsolete);
                    LOGGER.fine(() -> TranslationLogger.message("Pruned stale backup", Map.of("backup", obsolete.toString())));
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            LOGGER.log(Level.FINE, TranslationLogger.message("Failed to prune backups", Map.of("baseName", baseName)), e);
        }
    }
}
