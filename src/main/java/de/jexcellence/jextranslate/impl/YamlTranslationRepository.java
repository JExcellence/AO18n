package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.*;
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

    private static final Logger LOGGER = Logger.getLogger(YamlTranslationRepository.class.getName());
    private static final String CLASSPATH_PREFIX = "translations/";
    private static final String EXT = ".yml";

    private final Path translationsDirectory;
    private final Map<Locale, Map<String, String>> translations = new ConcurrentHashMap<>();
    private final List<RepositoryListener> listeners = new CopyOnWriteArrayList<>();
    private Locale defaultLocale;
    private long lastModified;

    public YamlTranslationRepository(@NotNull final Path translationsDirectory, @NotNull final Locale defaultLocale) {
        this.translationsDirectory = Objects.requireNonNull(translationsDirectory, "Translations directory cannot be null");
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "Default locale cannot be null");
        this.lastModified = System.currentTimeMillis();
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
                LOGGER.log(Level.SEVERE, "Failed to reload translations", exception);
                notifyError(exception);
            }
        });
    }

    private void loadTranslations() throws IOException {
        this.translations.clear();

        if (!Files.exists(this.translationsDirectory)) {
            Files.createDirectories(this.translationsDirectory);
            LOGGER.info("Created translations directory: " + this.translationsDirectory.toAbsolutePath());
        }

        int copied = copyBundledTranslationsToDirectory();

        int diskFiles = loadFromDirectory();

        if (diskFiles == 0) {
            LOGGER.warning("No translation files loaded from disk. Directory: " + this.translationsDirectory.toAbsolutePath()
                    + " | Newly copied from jar: " + copied);
        } else {
            LOGGER.info(String.format("Loaded translations for %d locales from disk. Files discovered: %d (copied from jar: %d)",
                    this.translations.size(), diskFiles, copied));
        }
    }

    private int copyBundledTranslationsToDirectory() {
        int copied = 0;
        try {
            CodeSource codeSource = YamlTranslationRepository.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                LOGGER.fine("No CodeSource available for YamlTranslationRepository; skipping bundled translations copy");
                return 0;
            }
            URL location = codeSource.getLocation();
            try (JarFile jarFile = new JarFile(location.getPath().replace("%20", " "))) {
                copied += copyFromJar(jarFile);
            }
        } catch (IOException io) {
            LOGGER.log(Level.FINE, "Direct JAR open failed; trying JarURLConnection approach", io);
            try {
                URL dirUrl = YamlTranslationRepository.class.getClassLoader().getResource(CLASSPATH_PREFIX);
                if (dirUrl != null) {
                    URLConnection conn = dirUrl.openConnection();
                    if (conn instanceof JarURLConnection jarURLConnection) {
                        try (JarFile jar = jarURLConnection.getJarFile()) {
                            copied += copyFromJar(jar);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to copy bundled translations from JAR", e);
                notifyError(e);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Unexpected error while copying bundled translations", ex);
            notifyError(ex);
        }
        return copied;
    }

    private int copyFromJar(JarFile jarFile) throws IOException {
        int copied = 0;
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory()) continue;
            if (!name.startsWith(CLASSPATH_PREFIX)) continue;
            if (!name.toLowerCase(Locale.ROOT).endsWith(EXT)) continue;

            String fileName = name.substring(CLASSPATH_PREFIX.length());
            Path target = this.translationsDirectory.resolve(fileName);
            if (Files.exists(target)) continue;

            try (InputStream in = YamlTranslationRepository.class.getClassLoader().getResourceAsStream(name)) {
                if (in == null) continue;
                Files.copy(new BufferedInputStream(in), target, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Copied default translations file: " + target.toAbsolutePath());
                copied++;
            }
        }
        return copied;
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
                LOGGER.fine("No entries in " + sourceType + " file: " + fileName + " for locale " + locale);
                return false;
            }

            final Map<String, String> flatMap = new HashMap<>();
            flattenMap("", data, flatMap);

            this.translations.put(locale, flatMap);
            LOGGER.fine("Loaded " + flatMap.size() + " translations for locale: " + locale + " from " + sourceType + " file " + fileName);

            for (final Map.Entry<String, String> entry : flatMap.entrySet()) {
                notifyTranslationLoaded(TranslationKey.of(entry.getKey()), locale, entry.getValue());
            }
            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to load translation file (" + sourceType + "): " + fileName, exception);
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
                LOGGER.log(Level.WARNING, "Listener error during reload notification", exception);
            }
        }
    }

    private void notifyTranslationLoaded(@NotNull final TranslationKey key, @NotNull final Locale locale, @NotNull final String translation) {
        for (final RepositoryListener listener : this.listeners) {
            try {
                listener.onTranslationLoaded(this, key, locale, translation);
            } catch (final Exception exception) {
                LOGGER.log(Level.WARNING, "Listener error during translation loaded notification", exception);
            }
        }
    }

    private void notifyError(@NotNull final Throwable error) {
        for (final RepositoryListener listener : this.listeners) {
            try {
                listener.onError(this, error);
            } catch (final Exception exception) {
                LOGGER.log(Level.WARNING, "Listener error during error notification", exception);
            }
        }
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