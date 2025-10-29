package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
import de.jexcellence.jextranslate.util.TranslationBackupService;
import de.jexcellence.jextranslate.util.TranslationLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class YamlTranslationRepository implements TranslationRepository {

    private static final Logger LOGGER = TranslationLogger.getLogger(YamlTranslationRepository.class);
    private static final String CLASSPATH_PREFIX = "translations/";
    private static final String EXT = ".yml";

    private final Path translationsDirectory;
    private final Map<Locale, Map<String, String>> translations = new ConcurrentHashMap<>();
    private final List<RepositoryListener> listeners = new CopyOnWriteArrayList<>();
    private final TranslationBackupService backupService;
    private final Set<Path> backedUpFiles = ConcurrentHashMap.newKeySet();
    private Locale defaultLocale;
    private long lastModified;

    public YamlTranslationRepository(@NotNull final Path translationsDirectory, @NotNull final Locale defaultLocale) {
        this.translationsDirectory = Objects.requireNonNull(translationsDirectory, "Translations directory cannot be null");
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "Default locale cannot be null");
        this.lastModified = System.currentTimeMillis();
        this.backupService = new TranslationBackupService(this.translationsDirectory);
    }

    @NotNull
    public static YamlTranslationRepository create(@NotNull final Path translationsDirectory, @NotNull final Locale defaultLocale) {
        final YamlTranslationRepository repository = new YamlTranslationRepository(translationsDirectory, defaultLocale);
        repository.reload().join();
        return repository;
    }

    @Override
    @NotNull
    public Optional<String> getTranslation(@NotNull final TranslationKey key, @NotNull final Locale locale) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        Optional<String> translation = getTranslationForLocale(key, locale);
        if (translation.isPresent()) return translation;

        if (!locale.getCountry().isEmpty()) {
            final Locale languageOnly = new Locale(locale.getLanguage());
            translation = getTranslationForLocale(key, languageOnly);
            if (translation.isPresent()) return translation;
        }

        if (!locale.equals(this.defaultLocale)) {
            translation = getTranslationForLocale(key, this.defaultLocale);
            if (translation.isPresent()) return translation;
        }

        return Optional.empty();
    }

    @NotNull
    private Optional<String> getTranslationForLocale(@NotNull final TranslationKey key, @NotNull final Locale locale) {
        final Map<String, String> localeTranslations = this.translations.get(locale);
        if (localeTranslations == null) return Optional.empty();
        return Optional.ofNullable(localeTranslations.get(key.key()));
    }

    @Override
    @NotNull
    public Set<Locale> getAvailableLocales() {
        return Set.copyOf(this.translations.keySet());
    }

    @Override
    @NotNull
    public Locale getDefaultLocale() {
        return this.defaultLocale;
    }

    @Override
    public void setDefaultLocale(@NotNull final Locale locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        if (!this.translations.containsKey(locale)) {
            throw new IllegalArgumentException("Locale not available: " + locale);
        }
        this.defaultLocale = locale;
    }

    @Override
    public synchronized boolean ensureTranslation(@NotNull final Locale locale, @NotNull final TranslationKey key, @NotNull final String defaultValue) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(defaultValue, "Default value cannot be null");

        final Map<String, String> localeTranslations = this.translations.computeIfAbsent(locale, ignored -> new ConcurrentHashMap<>());
        if (localeTranslations.containsKey(key.key())) {
            return false;
        }

        localeTranslations.put(key.key(), defaultValue);
        final Path file = resolveLocaleFile(locale);

        try {
            Files.createDirectories(file.getParent());
            createBackupIfNeeded(file, "ensure-translation");
            final boolean fileExists = Files.exists(file);
            final Yaml yaml = new Yaml();
            final Map<String, Object> data = fileExists ? yaml.load(Files.newInputStream(file)) : new LinkedHashMap<>();
            final Map<String, Object> effectiveData = data == null ? new LinkedHashMap<>() : data;

            // Un-flatten the key and insert it into the map
            Map<String, Object> current = effectiveData;
            String[] keyParts = key.key().split("\\.");
            for (int i = 0; i < keyParts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(keyParts[i], k -> new LinkedHashMap<>());
            }
            current.put(keyParts[keyParts.length - 1], defaultValue);

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                yaml.dump(effectiveData, writer);
            }
            this.lastModified = System.currentTimeMillis();
            notifyTranslationLoaded(key, locale, defaultValue);
            return true;
        } catch (final IOException ioException) {
            localeTranslations.remove(key.key());
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to append translation entry",
                            Map.of(
                                    "key", key.key(),
                                    "locale", locale.toString(),
                                    "file", file.toAbsolutePath().toString()
                            )
                    ),
                    ioException
            );
            notifyError(ioException);
            return false;
        }
    }

    @Override
    @NotNull
    public Set<TranslationKey> getAvailableKeys(@NotNull final Locale locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        final Map<String, String> localeTranslations = this.translations.get(locale);
        if (localeTranslations == null) {
            return Set.of();
        }
        return localeTranslations.keySet().stream()
                .map(TranslationKey::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @NotNull
    public Set<TranslationKey> getAllAvailableKeys() {
        return this.translations.values().stream()
                .flatMap(map -> map.keySet().stream())
                .distinct()
                .map(TranslationKey::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @NotNull
    public CompletableFuture<Void> reload() {
        return CompletableFuture.runAsync(() -> {
            try {
                loadTranslations();
                this.lastModified = System.currentTimeMillis();
                notifyReload();
            } catch (final Exception exception) {
                LOGGER.log(
                        Level.SEVERE,
                        TranslationLogger.message(
                                "Failed to reload translations",
                                Map.of("directory", this.translationsDirectory.toAbsolutePath().toString())
                        ),
                        exception
                );
                notifyError(exception);
            }
        });
    }

    private void loadTranslations() throws IOException {
        this.translations.clear();
        this.backedUpFiles.clear();

        if (!Files.exists(this.translationsDirectory)) {
            Files.createDirectories(this.translationsDirectory);
            LOGGER.info(() -> TranslationLogger.message(
                    "Created translations directory",
                    Map.of("directory", this.translationsDirectory.toAbsolutePath().toString())
            ));
        }

        final BundledSyncStats syncStats = synchronizeBundledTranslations();
        final int diskFiles = loadFromDirectory();

        if (diskFiles == 0) {
            LOGGER.warning(() -> TranslationLogger.message(
                    "No translation files loaded from disk",
                    Map.of(
                            "directory", this.translationsDirectory.toAbsolutePath().toString(),
                            "bundledCopies", syncStats.copied(),
                            "bundledUpdates", syncStats.updated()
                    )
            ));
        } else {
            LOGGER.info(() -> TranslationLogger.message(
                    "Loaded translations from disk",
                    Map.of(
                            "locales", this.translations.size(),
                            "diskFiles", diskFiles,
                            "bundledCopies", syncStats.copied(),
                            "bundledUpdates", syncStats.updated()
                    )
            ));
        }
    }

    private void createBackupIfNeeded(@NotNull final Path file, @NotNull final String reason) {
        if (!Files.exists(file)) {
            return;
        }
        final Path absolute = file.toAbsolutePath();
        if (!this.backedUpFiles.add(absolute)) {
            return;
        }
        try {
            this.backupService.createBackup(file, reason);
        } catch (final IOException exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to create translation backup",
                            Map.of(
                                    "file", absolute.toString(),
                                    "reason", reason
                            )
                    ),
                    exception
            );
            notifyError(exception);
        }
    }

    private BundledSyncStats synchronizeBundledTranslations() {
        final Set<String> processed = new HashSet<>();
        int copied = 0;
        int updated = 0;

        try {
            final CodeSource codeSource = YamlTranslationRepository.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                final URL location = codeSource.getLocation();
                try (JarFile jarFile = new JarFile(location.getPath().replace("%20", " "))) {
                    final BundledSyncStats stats = processJarEntries(jarFile, processed);
                    copied += stats.copied();
                    updated += stats.updated();
                }
            } else {
                LOGGER.fine(() -> TranslationLogger.message(
                        "No CodeSource available for bundled translation synchronisation",
                        Map.of("directory", this.translationsDirectory.toAbsolutePath().toString())
                ));
            }
        } catch (final IOException ioException) {
            LOGGER.log(
                    Level.FINE,
                    TranslationLogger.message(
                            "Direct JAR open failed; attempting JarURLConnection fallback",
                            Map.of("directory", this.translationsDirectory.toAbsolutePath().toString())
                    ),
                    ioException
            );
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, TranslationLogger.message(
                    "Unexpected error while synchronising bundled translations",
                    Map.of("directory", this.translationsDirectory.toAbsolutePath().toString())
            ), exception);
            notifyError(exception);
        }

        try {
            final URL dirUrl = YamlTranslationRepository.class.getClassLoader().getResource(CLASSPATH_PREFIX);
            if (dirUrl != null) {
                final URLConnection connection = dirUrl.openConnection();
                if (connection instanceof JarURLConnection jarURLConnection) {
                    try (JarFile jar = jarURLConnection.getJarFile()) {
                        final BundledSyncStats stats = processJarEntries(jar, processed);
                        copied += stats.copied();
                        updated += stats.updated();
                    }
                }
            }
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, TranslationLogger.message(
                    "Failed to synchronise bundled translations via classloader",
                    Map.of("directory", this.translationsDirectory.toAbsolutePath().toString())
            ), exception);
            notifyError(exception);
        }

        return new BundledSyncStats(copied, updated);
    }

    private BundledSyncStats processJarEntries(@NotNull final JarFile jarFile, @NotNull final Set<String> processed) throws IOException {
        int copied = 0;
        int updated = 0;
        final Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            final String name = entry.getName();
            if (entry.isDirectory()) {
                continue;
            }
            if (!name.startsWith(CLASSPATH_PREFIX)) {
                continue;
            }
            if (!name.toLowerCase(Locale.ROOT).endsWith(EXT)) {
                continue;
            }

            final BundledSyncStats stats = synchronizeBundledTranslation(
                    name,
                    () -> jarFile.getInputStream(entry),
                    processed
            );
            copied += stats.copied();
            updated += stats.updated();
        }
        return new BundledSyncStats(copied, updated);
    }

    private BundledSyncStats synchronizeBundledTranslation(
            @NotNull final String resourceName,
            @NotNull final IOSupplier<InputStream> supplier,
            @NotNull final Set<String> processed
    ) {
        if (!processed.add(resourceName)) {
            return BundledSyncStats.EMPTY;
        }

        final String fileName = resourceName.substring(CLASSPATH_PREFIX.length());
        final Path target = this.translationsDirectory.resolve(fileName);

        try {
            if (!Files.exists(target)) {
                try (InputStream in = supplier.get()) {
                    if (in == null) {
                        return BundledSyncStats.EMPTY;
                    }
                    Files.copy(new BufferedInputStream(in), target, StandardCopyOption.REPLACE_EXISTING);
                }
                LOGGER.info(() -> TranslationLogger.message(
                        "Copied default translation file",
                        Map.of("file", target.toAbsolutePath().toString())
                ));
                return new BundledSyncStats(1, 0);
            }

            final Map<String, String> bundledEntries = loadFlatTranslations(supplier);
            if (bundledEntries.isEmpty()) {
                return BundledSyncStats.EMPTY;
            }

            final Map<String, String> diskEntries = loadFlatTranslations(target);
            final Map<String, String> missingEntries = new LinkedHashMap<>();
            for (final Map.Entry<String, String> entry : bundledEntries.entrySet()) {
                if (!diskEntries.containsKey(entry.getKey())) {
                    missingEntries.put(entry.getKey(), entry.getValue());
                }
            }

            if (missingEntries.isEmpty()) {
                return BundledSyncStats.EMPTY;
            }

            appendMissingTranslations(target, missingEntries);
            return new BundledSyncStats(0, 1);
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to synchronise bundled translation",
                            Map.of(
                                    "resource", resourceName,
                                    "target", target.toAbsolutePath().toString()
                            )
                    ),
                    exception
            );
            notifyError(exception);
            return BundledSyncStats.EMPTY;
        }
    }

    private Map<String, String> loadFlatTranslations(@NotNull final IOSupplier<InputStream> supplier) throws IOException {
        try (InputStream inputStream = supplier.get()) {
            if (inputStream == null) {
                return Map.of();
            }
            final Yaml yaml = new Yaml();
            final Map<String, Object> data = yaml.load(inputStream);
            if (data == null || data.isEmpty()) {
                return Map.of();
            }
            final Map<String, String> flatMap = new LinkedHashMap<>();
            flattenMap("", data, flatMap);
            return flatMap;
        } catch (final FileNotFoundException ignored) {
            return Map.of();
        }
    }

    private Map<String, String> loadFlatTranslations(@NotNull final Path path) throws IOException {
        return loadFlatTranslations(() -> Files.newInputStream(path));
    }

    private void appendMissingTranslations(@NotNull final Path file, @NotNull final Map<String, String> missingEntries) throws IOException {
        createBackupIfNeeded(file, "merge-bundled-defaults");
        final StringBuilder builder = new StringBuilder();
        if (Files.size(file) > 0) {
            builder.append(System.lineSeparator());
        }
        for (final Map.Entry<String, String> entry : missingEntries.entrySet()) {
            builder.append(entry.getKey())
                    .append(": \"")
                    .append(escapeYaml(entry.getValue()))
                    .append("\"")
                    .append(System.lineSeparator());
        }
        Files.writeString(file, builder.toString(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        LOGGER.info(() -> TranslationLogger.message(
                "Appended missing bundled translations",
                Map.of(
                        "file", file.toAbsolutePath().toString(),
                        "entries", missingEntries.size()
                )
        ));
    }

    private int loadFromDirectory() throws IOException {
        if (!Files.exists(this.translationsDirectory)) {
            return 0;
        }
        int loadedFiles = 0;
        try (var paths = Files.list(this.translationsDirectory)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                String fileName = path.getFileName().toString();
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(EXT)) continue;

                if (loadTranslationFileFromStreamSupplier(
                        () -> Files.newInputStream(path),
                        fileName,
                        SourceType.DISK
                )) {
                    loadedFiles++;
                }
            }
        }
        return loadedFiles;
    }

    private boolean loadTranslationFileFromStreamSupplier(IOSupplier<InputStream> supplier, String fileName, SourceType sourceType) {
        try (final InputStream inputStream = supplier.get()) {
            if (inputStream == null) return false;

            final String baseName = fileName.substring(0, fileName.length() - EXT.length());
            final Locale locale = parseLocaleFlexible(baseName);

            final Yaml yaml = new Yaml();
            final Map<String, Object> data = yaml.load(inputStream);

            if (data == null || data.isEmpty()) {
                LOGGER.fine(() -> TranslationLogger.message(
                        "No translation entries discovered",
                        Map.of(
                                "source", sourceType.name().toLowerCase(Locale.ROOT),
                                "file", fileName,
                                "locale", locale.toString()
                        )
                ));
                return false;
            }

            final Map<String, String> flatMap = new HashMap<>();
            flattenMap("", data, flatMap);

            this.translations.put(locale, flatMap);
            LOGGER.fine(() -> TranslationLogger.message(
                    "Loaded translations from source",
                    Map.of(
                            "source", sourceType.name().toLowerCase(Locale.ROOT),
                            "file", fileName,
                            "locale", locale.toString(),
                            "entries", flatMap.size()
                    )
            ));

            for (final Map.Entry<String, String> entry : flatMap.entrySet()) {
                notifyTranslationLoaded(TranslationKey.of(entry.getKey()), locale, entry.getValue());
            }
            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to load translation file",
                            Map.of(
                                    "source", sourceType.name().toLowerCase(Locale.ROOT),
                                    "file", fileName
                            )
                    ),
                    exception
            );
            notifyError(exception);
            return false;
        }
    }

    private void flattenMap(@NotNull final String prefix, @NotNull final Map<String, Object> map, @NotNull final Map<String, String> result) {
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            final String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            final Object value = entry.getValue();

            if (value instanceof Map) {
                final Map<String, Object> nestedMap = (Map<String, Object>) value;
                flattenMap(key, nestedMap, result);
            } else if (value instanceof List) {
                final List<Object> list = (List<Object>) value;
                result.put(key, list.stream().map(String::valueOf).collect(Collectors.joining("\n")));
            } else if (value != null) {
                result.put(key, value.toString());
            }
        }
    }

    @NotNull
    private Locale parseLocaleFlexible(@NotNull final String localeStringRaw) {
        final String localeString = localeStringRaw.replace('-', '_');
        final String[] parts = localeString.split("_");
        if (parts.length == 1) {
            return new Locale(parts[0]);
        } else if (parts.length == 2) {
            return new Locale(parts[0], parts[1]);
        } else if (parts.length >= 3) {
            return new Locale(parts[0], parts[1], parts[2]);
        }
        return this.defaultLocale;
    }

    @Override
    public void addListener(@NotNull final RepositoryListener listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(@NotNull final RepositoryListener listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");
        this.listeners.remove(listener);
    }

    @Override
    @NotNull
    public RepositoryMetadata getMetadata() {
        return new MetadataImpl();
    }

    private void notifyReload() {
        for (final RepositoryListener listener : this.listeners) {
            try {
                listener.onReload(this);
            } catch (final Exception exception) {
                LOGGER.log(
                        Level.WARNING,
                        TranslationLogger.message(
                                "Listener error during reload notification",
                                Map.of("listener", listener.getClass().getName())
                        ),
                        exception
                );
            }
        }
    }

    private void notifyTranslationLoaded(@NotNull final TranslationKey key, @NotNull final Locale locale, @NotNull final String translation) {
        for (final RepositoryListener listener : this.listeners) {
            try {
                listener.onTranslationLoaded(this, key, locale, translation);
            } catch (final Exception exception) {
                LOGGER.log(
                        Level.WARNING,
                        TranslationLogger.message(
                                "Listener error during translation loaded notification",
                                Map.of(
                                        "listener", listener.getClass().getName(),
                                        "key", key.key(),
                                        "locale", locale.toString()
                                )
                        ),
                        exception
                );
            }
        }
    }

    private void notifyError(@NotNull final Throwable error) {
        for (final RepositoryListener listener : this.listeners) {
            try {
                listener.onError(this, error);
            } catch (final Exception exception) {
                LOGGER.log(
                        Level.WARNING,
                        TranslationLogger.message(
                                "Listener error during error notification",
                                Map.of("listener", listener.getClass().getName())
                        ),
                        exception
                );
            }
        }
    }

    private @NotNull Path resolveLocaleFile(@NotNull final Locale locale) {
        final String raw = locale.toString();
        final String localeTag = raw.isEmpty() ? locale.getLanguage() : raw;
        final String fileName = (localeTag == null || localeTag.isEmpty()) ? locale.getLanguage() : localeTag;
        return this.translationsDirectory.resolve(fileName + EXT);
    }

    private @NotNull String escapeYaml(@NotNull final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record BundledSyncStats(int copied, int updated) {
        private static final BundledSyncStats EMPTY = new BundledSyncStats(0, 0);
    }

    private enum SourceType {
        DISK
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }

    private final class MetadataImpl implements RepositoryMetadata {

        @Override
        @NotNull
        public String getType() {
            return "yaml";
        }

        @Override
        @NotNull
        public String getSource() {
            return translationsDirectory.toString();
        }

        @Override
        public long getLastModified() {
            return lastModified;
        }

        @Override
        public int getTotalTranslations() {
            return translations.values().stream()
                    .mapToInt(Map::size)
                    .sum();
        }

        @Override
        @Nullable
        public String getProperty(@NotNull final String key) {
            return switch (key) {
                case "directory" -> translationsDirectory.toString();
                case "locales" -> String.valueOf(translations.size());
                default -> null;
            };
        }
    }
}