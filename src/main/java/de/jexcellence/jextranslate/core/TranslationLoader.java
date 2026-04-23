package de.jexcellence.jextranslate.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.jexcellence.jextranslate.config.R18nConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.reader.ReaderException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Advanced translation loader with async support and comprehensive error handling.
 *
 * <p>This class handles loading, parsing, and managing translation files from both
 * plugin resources and the data directory. It supports hot-reloading, validation,
 * and provides detailed feedback about missing translations.</p>
 *
 * @author JExcellence
 * @version 3.0.0
 * @since 2.0.0
 */
public final class TranslationLoader {

    private final Logger logger;

    private final JavaPlugin plugin;
    private final R18nConfiguration configuration;
    private final Yaml yaml;
    private final ObjectMapper jsonMapper;

    // Thread-safe storage: Map<key, Map<locale, List<String>>>
    private final Map<String, Map<String, List<String>>> translations = new ConcurrentHashMap<>();
    private final Set<String> loadedLocales = ConcurrentHashMap.newKeySet();
    
    // Track which locales have YAML files (for precedence handling)
    private final Set<String> yamlLocales = ConcurrentHashMap.newKeySet();

    /**
     * Creates a translation loader for one plugin runtime.
     *
     * @param plugin        plugin that owns translation resources and data folder
     * @param configuration translation loading configuration
     */
    public TranslationLoader(@NotNull JavaPlugin plugin, @NotNull R18nConfiguration configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.logger = plugin.getLogger();
        this.yaml = new Yaml();
        this.jsonMapper = new ObjectMapper();
    }
    
    /**
     * Detects the file type based on file extension.
     *
     * @param filePath the path to the file
     * @return the detected file type
     */
    @NotNull
    public FileType detectFileType(@NotNull Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".json")) {
            return FileType.JSON;
        } else if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            return FileType.YAML;
        }
        return FileType.UNKNOWN;
    }
    
    /**
     * Enum representing supported translation file types.
     */
    public enum FileType {
        JSON,
        YAML,
        UNKNOWN
    }

    /**
     * Loads translations asynchronously from resource and data directories.
     *
     * @return a future that completes when translation loading finishes
     */
    @NotNull
    public CompletableFuture<Void> loadTranslations() {
        return CompletableFuture.runAsync(() -> {
            try {
                boolean quietMode = !configuration.debugMode();
                
                if (quietMode) {
                    logger.fine("Loading translations for plugin: " + plugin.getName());
                } else {
                    logger.info("Loading translations for plugin: " + plugin.getName());
                    logger.info("Plugin class: " + plugin.getClass().getName());
                    logger.info("Plugin data folder: " + plugin.getDataFolder().getAbsolutePath());
                }
                
                translations.clear();
                loadedLocales.clear();

                File translationDir = getTranslationDirectory();
                if (!quietMode) {
                    logger.info("Translation directory: " + translationDir.getAbsolutePath());
                }
                
                // Ensure plugin data folder exists first
                if (!plugin.getDataFolder().exists()) {
                    boolean created = plugin.getDataFolder().mkdirs();
                    if (!quietMode) {
                        logger.info("Created plugin data folder: " + created);
                    }
                }
                
                ensureDirectoryExists(translationDir);
                if (!quietMode) {
                    logger.info("Translation directory exists: " + translationDir.exists());
                    
                    // Check if we can find any resources
                    String testResource = configuration.translationDirectory() + "/en_US.yml";
                    InputStream testStream = plugin.getResource(testResource);
                    logger.info("Test resource '" + testResource + "' exists: " + (testStream != null));
                    if (testStream != null) {
                        try { testStream.close(); } catch (Exception ignored) {}
                    }
                }
                
                extractResourceFiles(translationDir);
                
                // List files in translation directory after extraction (only in debug mode)
                if (!quietMode) {
                    File[] files = translationDir.listFiles();
                    if (files != null) {
                        logger.info("Files in translation directory: " + files.length);
                        for (File file : files) {
                            logger.info("  - " + file.getName() + " (" + file.length() + " bytes)");
                        }
                    } else {
                        logger.warning("Translation directory is empty or not accessible");
                    }
                }
                
                loadTranslationFiles(translationDir);
                addProgrammaticTranslations();

                // Always log summary (but make it concise in quiet mode)
                logger.info(String.format("Successfully loaded %d locales with %d total translation keys",
                        loadedLocales.size(), getTotalKeyCount()));
                
                // Only log full locale list in debug mode
                if (!quietMode) {
                    logger.info("Loaded locales: " + String.join(", ", loadedLocales));
                }
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load translations", e);
                throw new RuntimeException("Translation loading failed", e);
            }
        });
    }

    /**
     * Returns the raw translation lines for a key and locale with fallback to default locale.
     *
     * @param key    translation key to resolve
     * @param locale locale code requested by the caller
     * @return optional raw lines when translation data exists
     */
    @NotNull
    public Optional<List<String>> getRawTranslation(@NotNull String key, @NotNull String locale) {
        Map<String, List<String>> localeMap = translations.get(key);
        if (localeMap == null) return Optional.empty();

        List<String> translation = localeMap.get(locale);
        if (translation != null && !translation.isEmpty()) {
            return Optional.of(new ArrayList<>(translation));
        }

        if (!locale.equals(configuration.defaultLocale())) {
            translation = localeMap.get(configuration.defaultLocale());
            if (translation != null && !translation.isEmpty()) {
                return Optional.of(new ArrayList<>(translation));
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether any translation lines exist for a key in the requested locale or fallback locale.
     *
     * @param key    translation key to resolve
     * @param locale locale code requested by the caller
     * @return {@code true} when at least one translation line exists
     */
    public boolean hasKey(@NotNull String key, @NotNull String locale) {
        return getRawTranslation(key, locale).isPresent();
    }

    /**
     * Returns all currently loaded translation keys.
     *
     * @return copy of all loaded keys
     */
    @NotNull
    public Set<String> getAllKeys() {
        return new HashSet<>(translations.keySet());
    }

    /**
     * Returns all locale codes loaded during the last load cycle.
     *
     * @return copy of loaded locale identifiers
     */
    @NotNull
    public Set<String> getLoadedLocales() {
        return new HashSet<>(loadedLocales);
    }

    /**
     * Returns the number of unique translation keys in memory.
     *
     * @return total number of loaded keys
     */
    public int getTotalKeyCount() {
        return translations.size();
    }

    /**
     * Returns keys that are unresolved for a locale, considering default-locale fallback.
     *
     * @param locale locale to validate
     * @return keys that are missing for the requested locale
     */
    @NotNull
    public Set<String> getMissingKeys(@NotNull String locale) {
        Set<String> missingKeys = new HashSet<>();
        String defaultLocale = configuration.defaultLocale();

        for (Map.Entry<String, Map<String, List<String>>> entry : translations.entrySet()) {
            String key = entry.getKey();
            Map<String, List<String>> localeMap = entry.getValue();

            List<String> localeTranslation = localeMap.get(locale);
            List<String> defaultTranslation = localeMap.get(defaultLocale);

            boolean missingInLocale = localeTranslation == null || localeTranslation.isEmpty();
            boolean missingInDefault = defaultTranslation == null || defaultTranslation.isEmpty();

            if (missingInLocale && missingInDefault) {
                missingKeys.add(key);
            } else if (missingInLocale && !locale.equals(defaultLocale)) {
                missingKeys.add(key);
            }
        }
        return missingKeys;
    }

    /**
     * Returns a defensive copy of all loaded translations.
     *
     * @return nested key and locale map containing all translation lines
     */
    @NotNull
    public Map<String, Map<String, List<String>>> getAllTranslations() {
        Map<String, Map<String, List<String>>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : translations.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Manually cleans up translation files for unsupported locales.
     * This can be called by users who want to remove files for locales not in their supportedLocales configuration.
     * 
     *
     * @return the number of files that were deleted
     */
    public int cleanupUnsupportedFiles() {
        Set<String> supportedLocales = configuration.supportedLocales();
        if (supportedLocales.isEmpty()) {
            logger.info("No supported locales configured - no cleanup needed");
            return 0;
        }
        
        File translationDir = getTranslationDirectory();
        if (!translationDir.exists()) {
            return 0;
        }
        
        File[] existingFiles = translationDir.listFiles();
        if (existingFiles == null) {
            return 0;
        }
        
        int deletedCount = 0;
        for (File file : existingFiles) {
            if (file.isFile() && isTranslationFile(file.getName())) {
                String locale = extractLocaleFromFilename(file.getName());
                
                // Delete if locale is not in supported list
                if (!supportedLocales.contains(locale)) {
                    try {
                        if (file.delete()) {
                            logger.info("Deleted unsupported locale file: " + file.getName());
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to delete unsupported locale file " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        if (deletedCount > 0) {
            logger.info("Manually cleaned up " + deletedCount + " unsupported locale files");
        } else {
            logger.info("No unsupported locale files found to clean up");
        }
        
        return deletedCount;
    }

    @NotNull
    private File getTranslationDirectory() {
        return new File(plugin.getDataFolder(), configuration.translationDirectory());
    }

    private void ensureDirectoryExists(@NotNull File directory) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new RuntimeException("Failed to create translation directory: " + directory.getAbsolutePath());
        }
    }

    /**
     * Cleans up translation files for unsupported locales if a supported-locale filter is configured.
     * This removes files that were extracted in previous runs with different configurations.
     */
    private void cleanupUnsupportedLocaleFiles(@NotNull File translationDir) {
        Set<String> supportedLocales = configuration.supportedLocales();
        if (supportedLocales.isEmpty()) {
            return; // Auto-detect mode — keep all files
        }
        
        File[] existingFiles = translationDir.listFiles();
        if (existingFiles == null) {
            return;
        }
        
        int deletedCount = 0;
        for (File file : existingFiles) {
            if (file.isFile() && isTranslationFile(file.getName())) {
                String locale = extractLocaleFromFilename(file.getName());
                
                // Delete if locale is not in supported list
                if (!supportedLocales.contains(locale)) {
                    try {
                        if (file.delete()) {
                            logger.info("Deleted unsupported locale file: " + file.getName());
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to delete unsupported locale file " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        if (deletedCount > 0) {
            logger.info("Cleaned up " + deletedCount + " unsupported locale files (debug mode)");
        }
    }

    private void extractResourceFiles(@NotNull File translationDir) {
        String translationPath = configuration.translationDirectory();
        cleanupUnsupportedLocaleFiles(translationDir);
        extractUsingJarScanning(translationDir, translationPath);
    }
    
    /**
     * Extracts translation files by scanning the JAR file directly.
     * This is a fallback method that catches any files not covered by the Bukkit method.
     * Only extracts files for locales in supportedLocales (if configured).
     */
    private void extractUsingJarScanning(@NotNull File translationDir, @NotNull String translationPath) {
        try {
            // Try multiple methods to get the JAR file
            File jarFile = findPluginJarFile();
            if (jarFile == null) {
                logger.fine("Could not locate plugin JAR file for scanning");
                return;
            }
            
            boolean quietMode = !configuration.debugMode();
            if (!quietMode) {
                logger.info("Scanning JAR for additional translation files: " + jarFile.getName());
            }
            
            Set<String> supportedLocales = configuration.supportedLocales();
            boolean filterEnabled = !supportedLocales.isEmpty();
            
            if (!quietMode) {
                if (filterEnabled) {
                    logger.info("JAR scanning will filter for supported locales: " + String.join(", ", supportedLocales));
                } else {
                    logger.info("JAR scanning with no locale filtering");
                }
            }
            
            try (var jar = new java.util.jar.JarFile(jarFile)) {
                var entries = jar.entries();
                int extractedCount = 0;
                int skippedCount = 0;
                
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String entryName = entry.getName();
                    
                    // Check if this is a translation file in the translations directory
                    if (entryName.startsWith(translationPath + "/") && 
                            isTranslationFile(entryName) && 
                            !entry.isDirectory()) {
                        
                        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                        String locale = extractLocaleFromFilename(fileName);
                        
                        // Skip if filtering is enabled and locale not in supported list
                        if (filterEnabled && !supportedLocales.contains(locale)) {
                            logger.fine("Skipping JAR extraction of unsupported locale: " + locale);
                            skippedCount++;
                            continue;
                        }
                        
                        File targetFile = new File(translationDir, fileName);
                        
                        if (!targetFile.exists()) {
                            try (InputStream in = jar.getInputStream(entry)) {
                                if (in != null) {
                                    Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    logger.fine("Extracted translation file via JAR scan: " + fileName);
                                    extractedCount++;
                                }
                            }
                        }
                    }
                }
                
                if (extractedCount > 0 && !quietMode) {
                    logger.info("Extracted " + extractedCount + " additional translation files from JAR");
                }
                if (skippedCount > 0) {
                    logger.fine("Skipped " + skippedCount + " unsupported locales during JAR extraction");
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to scan JAR for translation files: " + e.getMessage(), e);
        }
    }
    
    /**
     * Attempts to find the plugin's JAR file using multiple methods.
     */
    @Nullable
    private File findPluginJarFile() {
        // Method 1: Try via code source
        try {
            var codeSource = plugin.getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                File jarFile = new File(codeSource.getLocation().toURI());
                if (jarFile.exists() && jarFile.getName().endsWith(".jar")) {
                    return jarFile;
                }
            }
        } catch (Exception e) {
            logger.fine("Could not get JAR via code source: " + e.getMessage());
        }
        
        // Method 2: Try via JavaPlugin.getFile() (protected method — must use getDeclaredMethod)
        try {
            var getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
            getFileMethod.setAccessible(true);
            File jarFile = (File) getFileMethod.invoke(plugin);
            if (jarFile != null && jarFile.exists()) {
                return jarFile;
            }
        } catch (Exception e) {
            logger.fine("Could not get JAR via getFile(): " + e.getMessage());
        }
        
        // Method 3: Look in plugins folder
        try {
            File pluginsFolder = plugin.getDataFolder().getParentFile();
            String pluginName = plugin.getName();
            File[] jarFiles = pluginsFolder.listFiles((dir, name) -> 
                name.toLowerCase().contains(pluginName.toLowerCase()) && name.endsWith(".jar"));
            if (jarFiles != null && jarFiles.length > 0) {
                return jarFiles[0];
            }
        } catch (Exception e) {
            logger.fine("Could not find JAR in plugins folder: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Checks if a file name represents a supported translation file.
     *
     * @param fileName the file name to check
     * @return true if the file is a supported translation file (.yml, .yaml, or .json)
     */
    private boolean isTranslationFile(@NotNull String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".yml") || lowerName.endsWith(".yaml") || lowerName.endsWith(".json");
    }
    
    /**
     * Extracts the locale code from a translation filename.
     * 
     *
     * @param fileName the filename (e.g., "en_US.yml", "de_DE.json")
     * @return the locale code (e.g., "en_US", "de_DE")
     */
    @NotNull
    private String extractLocaleFromFilename(@NotNull String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private void loadTranslationFiles(@NotNull File translationDir) {
        yamlLocales.clear();
        
        try (Stream<Path> paths = Files.walk(translationDir.toPath())) {
            // First pass: load YAML files and track which locales have YAML
            paths.filter(path -> {
                        String name = path.toString().toLowerCase();
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .filter(Files::isRegularFile)
                    .forEach(this::loadTranslationFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan translation directory for YAML files", e);
        }
        
        try (Stream<Path> paths = Files.walk(translationDir.toPath())) {
            // Second pass: load JSON files (only for locales without YAML)
            paths.filter(path -> path.toString().toLowerCase().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .forEach(this::loadTranslationFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan translation directory for JSON files", e);
        }
    }

    private void loadTranslationFile(@NotNull Path filePath) {
        FileType fileType = detectFileType(filePath);
        
        switch (fileType) {
            case YAML -> loadYamlFile(filePath);
            case JSON -> loadJsonFile(filePath);
            default -> {
                if (configuration.debugMode()) {
                    logger.warning("Unsupported file type: " + filePath.getFileName());
                }
            }
        }
    }
    
    /**
     * Loads a YAML translation file.
     * Locales are auto-detected from file names - no need to pre-configure supported locales.
     *
     * @param filePath the path to the YAML file
     */
    @SuppressWarnings("unchecked")
    private void loadYamlFile(@NotNull Path filePath) {
        String fileName = filePath.getFileName().toString();
        String locale = fileName.substring(0, fileName.lastIndexOf('.'));

        // Auto-detect locales from files - only skip if explicitly configured and locale not in list
        if (!configuration.supportedLocales().isEmpty() && !configuration.supportedLocales().contains(locale)) {
            logger.fine("Skipping unsupported locale: " + locale);
            return;
        }

        try {
            boolean quietMode = !configuration.debugMode();
            if (!quietMode) {
                logger.info("Loading YAML translation file: " + fileName);
            } else {
                logger.fine("Loading YAML translation file: " + fileName);
            }
            
            Map<String, Object> data;
            String rawContent = Files.readString(filePath, StandardCharsets.UTF_8);
            String normalizedContent = TranslationContentRepair.stripUtf8Bom(rawContent);

            try {
                data = yaml.load(normalizedContent);
            } catch (ReaderException readerException) {
                Optional<String> repairedContent = TranslationContentRepair.repairCorruptedUtf8Content(normalizedContent);
                if (repairedContent.isEmpty()) {
                    throw readerException;
                }

                data = yaml.load(repairedContent.get());
                Files.writeString(filePath, repairedContent.get(), StandardCharsets.UTF_8);
                logger.warning("Recovered corrupted UTF-8 translation file and rewrote it as valid UTF-8: " + fileName);
            }

            if (data == null) {
                logger.warning("Translation file is empty: " + fileName);
                return;
            }

            Map<String, Object> flattened = flattenMap(data);
            for (Map.Entry<String, Object> entry : flattened.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                List<String> messages = convertToStringList(value);

                if (!messages.isEmpty()) {
                    translations.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(locale, messages);
                }
            }

            loadedLocales.add(locale);
            yamlLocales.add(locale);
            logger.fine(String.format("Loaded %d keys for locale %s from YAML", flattened.size(), locale));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load YAML translation file: " + fileName, e);
        }
    }
    
    /**
     * Loads a JSON translation file.
     * JSON files are only loaded if no YAML file exists for the same locale (YAML takes precedence).
     *
     * @param filePath the path to the JSON file
     */
    private void loadJsonFile(@NotNull Path filePath) {
        String fileName = filePath.getFileName().toString();
        String locale = fileName.substring(0, fileName.lastIndexOf('.'));

        // Auto-detect locales from files - only skip if explicitly configured and locale not in list
        if (!configuration.supportedLocales().isEmpty() && !configuration.supportedLocales().contains(locale)) {
            logger.fine("Skipping unsupported locale: " + locale);
            return;
        }
        
        // YAML takes precedence - skip JSON if YAML already loaded for this locale
        if (yamlLocales.contains(locale)) {
            logger.fine("Skipping JSON file for locale " + locale + " (YAML takes precedence)");
            return;
        }

        try {
            boolean quietMode = !configuration.debugMode();
            if (!quietMode) {
                logger.info("Loading JSON translation file: " + fileName);
            } else {
                logger.fine("Loading JSON translation file: " + fileName);
            }
            
            Map<String, Object> data;
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                data = jsonMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
            }

            if (data == null || data.isEmpty()) {
                logger.warning("Translation file is empty: " + fileName);
                return;
            }

            Map<String, Object> flattened = flattenMap(data);
            for (Map.Entry<String, Object> entry : flattened.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                List<String> messages = convertToStringList(value);

                if (!messages.isEmpty()) {
                    translations.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(locale, messages);
                }
            }

            loadedLocales.add(locale);
            logger.fine(String.format("Loaded %d keys for locale %s from JSON", flattened.size(), locale));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load JSON translation file: " + fileName, e);
        }
    }

    @NotNull
    private Map<String, Object> flattenMap(@NotNull Map<String, Object> map) {
        Map<String, Object> flattened = new HashMap<>();
        flattenMapRecursive("", map, flattened);
        return flattened;
    }

    @SuppressWarnings("unchecked")
    private void flattenMapRecursive(@NotNull String prefix, @NotNull Map<String, Object> map, @NotNull Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                flattenMapRecursive(key, (Map<String, Object>) value, result);
            } else {
                result.put(key, value);
            }
        }
    }

    @NotNull
    private List<String> convertToStringList(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream().map(Object::toString).toList();
        }
        // YAML `|-` block scalars come in as a single string with
        // embedded newlines. Minecraft item lore is a List<Component>,
        // one line per element, so we split here and hand the caller
        // a proper multi-line shape. Scalars without a newline fall
        // through to a single-element list, unchanged from before.
        final String raw = value.toString();
        if (raw.indexOf('\n') < 0) return List.of(raw);
        return java.util.Arrays.asList(raw.split("\n", -1));
    }

    private void addProgrammaticTranslations() {
        // English (US) translations
        addInternalTranslation("r18n.reload.success", "en_US", "<green>✓ Translations reloaded successfully!</green>");
        addInternalTranslation("r18n.reload.failure", "en_US", "<red>✗ Failed to reload translations!</red>");
        addInternalTranslation("r18n.missing.none", "en_US", "<green>✓ No missing translation keys found!</green>");
        addInternalTranslation("r18n.missing.header", "en_US", "<yellow>Found {count} missing keys for locale '{locale}':</yellow>");
        addInternalTranslation("r18n.missing.key", "en_US", "<red> ✗ {key}</red>");
        addInternalTranslation("r18n.key.missing", "en_US", "<gold>Missing key: <red>{key}</red></gold>");

        // German (Germany) translations
        addInternalTranslation("r18n.reload.success", "de_DE", "<green>✓ Übersetzungen erfolgreich neu geladen!</green>");
        addInternalTranslation("r18n.reload.failure", "de_DE", "<red>✗ Fehler beim Neuladen der Übersetzungen!</red>");
        addInternalTranslation("r18n.missing.none", "de_DE", "<green>✓ Keine fehlenden Übersetzungsschlüssel gefunden!</green>");
        addInternalTranslation("r18n.missing.header", "de_DE", "<yellow>{count} fehlende Schlüssel für Sprache '{locale}' gefunden:</yellow>");
        addInternalTranslation("r18n.key.missing", "de_DE", "<gold>Fehlender Schlüssel: <red>{key}</red></gold>");

        // Spanish (Spain) translations
        addInternalTranslation("r18n.reload.success", "es_ES", "<green>✓ ¡Traducciones recargadas exitosamente!</green>");
        addInternalTranslation("r18n.reload.failure", "es_ES", "<red>✗ ¡Error al recargar las traducciones!</red>");
        addInternalTranslation("r18n.missing.none", "es_ES", "<green>✓ ¡No se encontraron claves de traducción faltantes!</green>");
        addInternalTranslation("r18n.missing.header", "es_ES", "<yellow>Se encontraron {count} claves faltantes para el idioma '{locale}':</yellow>");
        addInternalTranslation("r18n.key.missing", "es_ES", "<gold>Clave faltante: <red>{key}</red></gold>");
    }

    private void addInternalTranslation(@NotNull String key, @NotNull String locale, @NotNull String message) {
        translations.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(locale, List.of(message));
    }
}
