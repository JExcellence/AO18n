package de.jexcellence.jextranslate;

import de.jexcellence.jextranslate.command.PR18nCommand;
import de.jexcellence.jextranslate.config.R18nConfiguration;
import de.jexcellence.jextranslate.core.MessageProvider;
import de.jexcellence.jextranslate.core.TranslationFileWatcher;
import de.jexcellence.jextranslate.core.TranslationLoader;
import de.jexcellence.jextranslate.core.VersionDetector;
import de.jexcellence.jextranslate.core.VersionedMessageSender;
import de.jexcellence.jextranslate.storage.DatabaseLocaleStorage;
import de.jexcellence.jextranslate.storage.InMemoryLocaleStorage;
import de.jexcellence.jextranslate.storage.LocaleStorage;
import de.jexcellence.jextranslate.validation.KeyValidator;
import jakarta.persistence.EntityManagerFactory;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modern R18n Manager for Minecraft plugins supporting versions 1.8-1.21+.
 *
 * <p>This is the main entry point for the R18n internationalization system.
 * It provides a fluent API for configuration, translation loading, and message handling
 * with full support for MiniMessage formatting and legacy color codes.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Universal version support (1.8-1.21+)</li>
 *   <li>Automatic Paper/Spigot/Bukkit detection</li>
 *   <li>MiniMessage with legacy fallback</li>
 *   <li>Comprehensive key validation</li>
 *   <li>Async translation loading</li>
 *   <li>PlaceholderAPI integration</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * R18nManager r18n = R18nManager.builder(plugin)
 *     .defaultLocale("en_GB")
 *     .supportedLocales("en_GB", "de_DE", "es_ES", "fr_FR")
 *     .enableKeyValidation(true)
 *     .enablePlaceholderAPI(true)
 *     .build();
 *
 * // Initialize asynchronously
 * r18n.initialize().thenRun(() -> {
 *     // Send a message
 *     r18n.message("welcome.player")
 *         .placeholder("player", player.getName())
 *         .placeholder("server", server.getName())
 *         .send(player);
 * });
 * }</pre>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class R18nManager {

    // Static instance for legacy wrapper compatibility - using AtomicReference for thread safety
    private static final AtomicReference<R18nManager> instance = new AtomicReference<>();

    private final JavaPlugin plugin;
    private final R18nConfiguration configuration;
    private final TranslationLoader translationLoader;
    private final MessageProvider messageProvider;
    private final KeyValidator keyValidator;
    private final VersionDetector versionDetector;
    private final LocaleStorage localeStorage;
    private final Logger logger;

    private BukkitAudiences audiences;
    private VersionedMessageSender messageSender;
    private TranslationFileWatcher fileWatcher;
    private Thread fileWatcherThread;
    private boolean initialized = false;

    /**
     * Gets the current R18nManager instance.
     * Used by legacy wrappers for backwards compatibility.
     *
     * <p>This method is thread-safe and uses an AtomicReference internally.</p>
     *
     * @return the current instance, or null if not initialized
     */
    @Nullable
    public static R18nManager getInstance() {
        return instance.get();
    }

    /**
     * Private constructor - use {@link #builder(JavaPlugin)} to create instances.
     */
    private R18nManager(@NotNull Builder builder) {
        this.plugin = builder.plugin;
        this.configuration = builder.configuration;
        this.logger = plugin.getLogger();
        this.versionDetector = new VersionDetector();
        this.translationLoader = new TranslationLoader(plugin, configuration);
        this.keyValidator = new KeyValidator(configuration);
        this.messageProvider = new MessageProvider(configuration, versionDetector);
        this.localeStorage = builder.localeStorage != null ? builder.localeStorage : new InMemoryLocaleStorage();
    }

    /**
     * Creates a new builder for configuring R18n.
     *
     * @param plugin the plugin instance
     * @return a new builder instance
     */
    @NotNull
    public static Builder builder(@NotNull JavaPlugin plugin) {
        return new Builder(plugin);
    }

    /**
     * Initializes the R18n system.
     *
     * @return a CompletableFuture that completes when initialization is done
     */
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Initializing R18n v2.0.0...");

                // Initialize Adventure platform
                initializeAdventure();

                // Initialize versioned message sender
                messageSender = new VersionedMessageSender(versionDetector, audiences);

                // Load translations
                translationLoader.loadTranslations().join();

                // Connect message provider with translation loader
                messageProvider.setTranslationLoader(translationLoader);

                // Validate keys if enabled
                if (configuration.keyValidationEnabled()) {
                    keyValidator.validateAllKeys();
                }

                // Start file watcher if enabled
                if (configuration.watchFiles()) {
                    startFileWatcher();
                }

                initialized = true;
                instance.set(this);

                int languageCount = translationLoader.getLoadedLocales().size();
                int keyCount = translationLoader.getTotalKeyCount();
                
                // Modern mode requires both version support AND audiences being available
                boolean modernMode = versionDetector.isModern() && audiences != null;

                logger.info(String.format(
                        "R18n initialized successfully! Loaded %d languages with %d translation keys. " +
                                "Server: %s, Version: %s, Modern: %s",
                        languageCount,
                        keyCount,
                        versionDetector.getServerType(),
                        versionDetector.getMinecraftVersion(),
                        modernMode
                ));

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize R18n", e);
                throw new RuntimeException("R18n initialization failed", e);
            }
        });
    }

    /**
     * Creates a new message builder for the specified key.
     *
     * @param key the translation key
     * @return a new message builder
     */
    @NotNull
    public MessageBuilder message(@NotNull String key) {
        if (!initialized) {
            throw new IllegalStateException("R18n is not initialized. Call initialize() first.");
        }
        return new MessageBuilder(this, key);
    }

    /**
     * Reloads all translations.
     *
     * @return a CompletableFuture that completes when reloading is done
     */
    @NotNull
    public CompletableFuture<Void> reload() {
        return translationLoader.loadTranslations().thenRun(() -> {
            // Invalidate cache on reload
            messageProvider.invalidateCache();

            if (configuration.keyValidationEnabled()) {
                keyValidator.validateAllKeys();
            }
            logger.info("R18n translations reloaded successfully");
        });
    }

    /**
     * Shuts down the R18n system and releases resources.
     */
    public void shutdown() {
        // Stop file watcher if running
        stopFileWatcher();
        
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
        messageSender = null;
        initialized = false;
        instance.set(null);
        logger.info("R18n shutdown complete");
    }

    /**
     * Gets cache statistics for monitoring translation cache performance.
     *
     * @return cache statistics, or null if caching is disabled
     */
    @Nullable
    public com.github.benmanes.caffeine.cache.stats.CacheStats getCacheStats() {
        return messageProvider.getCacheStats();
    }

    /**
     * Gets translation metrics for monitoring usage statistics.
     *
     * @return the metrics instance, or null if metrics are disabled
     */
    @Nullable
    public de.jexcellence.jextranslate.core.TranslationMetrics getMetrics() {
        return messageProvider.getMetrics();
    }

    /**
     * Exports all translations to the specified format.
     *
     * <p>This method exports all loaded translations to a file in the specified format.
     * Supported formats are CSV, JSON, and YAML.</p>
     *
     * @param outputPath the path to write the export file
     * @param format     the export format
     * @throws java.io.IOException if an I/O error occurs during export
     * @see de.jexcellence.jextranslate.core.TranslationExportService.ExportFormat
     */
    public void exportTranslations(@NotNull java.nio.file.Path outputPath, 
                                    @NotNull de.jexcellence.jextranslate.core.TranslationExportService.ExportFormat format) 
            throws java.io.IOException {
        de.jexcellence.jextranslate.core.TranslationExportService exportService = 
                new de.jexcellence.jextranslate.core.TranslationExportService();
        exportService.export(outputPath, format, translationLoader.getAllTranslations());
    }

    /**
     * Gets the configuration.
     *
     * @return the configuration
     */
    @NotNull
    public R18nConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Gets the translation loader.
     *
     * @return the translation loader
     */
    @NotNull
    public TranslationLoader getTranslationLoader() {
        return translationLoader;
    }

    /**
     * Gets the message provider.
     *
     * @return the message provider
     */
    @NotNull
    public MessageProvider getMessageProvider() {
        return messageProvider;
    }

    /**
     * Gets the key validator.
     *
     * @return the key validator
     */
    @NotNull
    public KeyValidator getKeyValidator() {
        return keyValidator;
    }

    /**
     * Gets the version detector.
     *
     * @return the version detector
     */
    @NotNull
    public VersionDetector getVersionDetector() {
        return versionDetector;
    }

    /**
     * Gets the Adventure audiences instance.
     *
     * @return the audiences instance, or null if not initialized
     */
    @Nullable
    public BukkitAudiences getAudiences() {
        return audiences;
    }

    /**
     * Gets the versioned message sender.
     *
     * @return the versioned message sender, or null if not initialized
     */
    @Nullable
    public VersionedMessageSender getMessageSender() {
        return messageSender;
    }

    /**
     * Gets the locale storage.
     *
     * @return the locale storage
     */
    @NotNull
    public LocaleStorage getLocaleStorage() {
        return localeStorage;
    }

    /**
     * Gets the plugin instance.
     *
     * @return the plugin instance
     */
    @NotNull
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Checks if R18n is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Registers the R18n administration command.
     *
     * <p>This method registers the "/r18n" command which provides subcommands for:</p>
     * <ul>
     *   <li>reload - Reload translation files</li>
     *   <li>missing - View missing translation keys</li>
     *   <li>export - Export translations to CSV/JSON/YAML</li>
     *   <li>metrics - View translation usage statistics</li>
     * </ul>
     *
     * <p>Note: The command must be defined in your plugin.yml:</p>
     * <pre>
     * commands:
     *   r18n:
     *     description: R18n translation management commands
     *     usage: /r18n [reload|missing|export|metrics]
     *     permission: r18n.admin
     * </pre>
     *
     * @return true if the command was registered successfully, false otherwise
     */
    public boolean registerCommand() {
        return registerCommand("r18n");
    }

    /**
     * Registers the R18n administration command with a custom command name.
     *
     * <p>This allows you to use a different command name if "r18n" conflicts
     * with another plugin or you prefer a different name.</p>
     *
     * @param commandName the command name to register (must be defined in plugin.yml)
     * @return true if the command was registered successfully, false otherwise
     */
    public boolean registerCommand(@NotNull String commandName) {
        if (!initialized) {
            logger.warning("Cannot register command before R18n is initialized. Call initialize() first.");
            return false;
        }

        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            logger.warning("Command '" + commandName + "' not found in plugin.yml. " +
                    "Please add the following to your plugin.yml:\n" +
                    "commands:\n" +
                    "  " + commandName + ":\n" +
                    "    description: R18n translation management commands\n" +
                    "    usage: /" + commandName + " [reload|missing|export|metrics]\n" +
                    "    permission: r18n.admin");
            return false;
        }

        PR18nCommand commandHandler = new PR18nCommand(plugin, this);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        logger.info("Registered /" + commandName + " command for R18n administration");
        return true;
    }

    /**
     * Initializes the Adventure platform with graceful fallback.
     */
    private void initializeAdventure() {
        try {
            // Try to create BukkitAudiences - this may fail if adventure-platform-bukkit is not available
            audiences = BukkitAudiences.create(plugin);
            logger.info("Adventure platform initialized for " + versionDetector.getServerType());
        } catch (NoClassDefFoundError | Exception e) {
            // Gracefully handle missing BukkitAudiences - fall back to legacy mode
            logger.info("Adventure platform not available, using legacy mode: " + e.getMessage());
            audiences = null;
        }
    }

    /**
     * Starts the file watcher for hot reload functionality.
     */
    private void startFileWatcher() {
        try {
            File translationDir = new File(plugin.getDataFolder(), configuration.translationDirectory());
            if (!translationDir.exists()) {
                logger.warning("Translation directory does not exist, file watcher not started");
                return;
            }
            
            fileWatcher = new TranslationFileWatcher(
                    translationDir.toPath(),
                    () -> {
                        logger.info("File change detected, reloading translations...");
                        reload().exceptionally(ex -> {
                            logger.log(Level.SEVERE, "Failed to reload translations after file change", ex);
                            return null;
                        });
                    }
            );
            
            fileWatcherThread = new Thread(fileWatcher, "R18n-FileWatcher");
            fileWatcherThread.setDaemon(true);
            fileWatcherThread.start();
            
            logger.info("File watcher started for hot reload");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to start file watcher", e);
        }
    }

    /**
     * Stops the file watcher if it is running.
     */
    private void stopFileWatcher() {
        if (fileWatcher != null) {
            fileWatcher.stop();
            fileWatcher = null;
        }
        if (fileWatcherThread != null) {
            fileWatcherThread.interrupt();
            try {
                fileWatcherThread.join(2000); // Wait up to 2 seconds for thread to stop
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            fileWatcherThread = null;
        }
    }


    /**
     * Builder for creating R18nManager instances.
     */
    public static final class Builder {
        private final JavaPlugin plugin;
        private R18nConfiguration configuration;
        private LocaleStorage localeStorage;
        private EntityManagerFactory entityManagerFactory;

        private Builder(@NotNull JavaPlugin plugin) {
            this.plugin = plugin;
            this.configuration = R18nConfiguration.defaultConfiguration();
        }

        /**
         * Sets the EntityManagerFactory for database operations.
         *
         * <p>This EMF can be used by {@link #withDatabaseStorage()} to create
         * a database-backed locale storage without passing the EMF again.</p>
         *
         * @param entityManagerFactory the JPA entity manager factory
         * @return this builder
         */
        @NotNull
        public Builder entityManagerFactory(@NotNull EntityManagerFactory entityManagerFactory) {
            this.entityManagerFactory = entityManagerFactory;
            return this;
        }

        /**
         * Sets the default locale.
         *
         * @param locale the default locale
         * @return this builder
         */
        @NotNull
        public Builder defaultLocale(@NotNull String locale) {
            configuration = configuration.withDefaultLocale(locale);
            return this;
        }

        /**
         * Sets the supported locales.
         *
         * @param locales the supported locales
         * @return this builder
         */
        @NotNull
        public Builder supportedLocales(@NotNull String... locales) {
            configuration = configuration.withSupportedLocales(locales);
            return this;
        }

        /**
         * Enables auto-detection of locales from translation files.
         * All translation files found in the translation directory will be loaded
         * regardless of their locale code.
         *
         * @return this builder
         */
        @NotNull
        public Builder autoDetectLocales() {
            configuration = configuration.withAutoDetectLocales();
            return this;
        }

        /**
         * Enables or disables key validation.
         *
         * @param enabled whether to enable key validation
         * @return this builder
         */
        @NotNull
        public Builder enableKeyValidation(boolean enabled) {
            configuration = configuration.withKeyValidationEnabled(enabled);
            return this;
        }

        /**
         * Enables or disables PlaceholderAPI integration.
         *
         * @param enabled whether to enable PlaceholderAPI
         * @return this builder
         */
        @NotNull
        public Builder enablePlaceholderAPI(boolean enabled) {
            configuration = configuration.withPlaceholderAPIEnabled(enabled);
            return this;
        }

        /**
         * Sets the translation directory name.
         *
         * @param directory the directory name
         * @return this builder
         */
        @NotNull
        public Builder translationDirectory(@NotNull String directory) {
            configuration = configuration.withTranslationDirectory(directory);
            return this;
        }

        /**
         * Sets a custom configuration.
         *
         * @param configuration the configuration
         * @return this builder
         */
        @NotNull
        public Builder configuration(@NotNull R18nConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        /**
         * Sets a custom locale storage implementation.
         *
         * @param localeStorage the locale storage implementation
         * @return this builder
         */
        @NotNull
        public Builder localeStorage(@NotNull LocaleStorage localeStorage) {
            this.localeStorage = localeStorage;
            return this;
        }

        /**
         * Configures database-backed locale storage using JPA.
         *
         * <p>This method creates a {@link DatabaseLocaleStorage} using the provided
         * EntityManagerFactory. The storage will persist player locale preferences
         * to the database.</p>
         *
         * @param entityManagerFactory the JPA entity manager factory
         * @return this builder
         */
        @NotNull
        public Builder withDatabaseStorage(@NotNull EntityManagerFactory entityManagerFactory) {
            this.localeStorage = new DatabaseLocaleStorage(entityManagerFactory);
            return this;
        }

        /**
         * Configures database-backed locale storage using the previously set EntityManagerFactory.
         *
         * <p>This method creates a {@link DatabaseLocaleStorage} using the EMF set via
         * {@link #entityManagerFactory(EntityManagerFactory)}. Call that method first
         * before calling this one.</p>
         *
         * @return this builder
         * @throws IllegalStateException if no EntityManagerFactory has been set
         */
        @NotNull
        public Builder withDatabaseStorage() {
            if (this.entityManagerFactory == null) {
                throw new IllegalStateException(
                    "EntityManagerFactory must be set via entityManagerFactory() before calling withDatabaseStorage()");
            }
            this.localeStorage = new DatabaseLocaleStorage(this.entityManagerFactory);
            return this;
        }

        /**
         * Enables or disables file watching for hot reload.
         *
         * <p>When enabled, the system will automatically reload translations
         * when translation files are modified, created, or deleted.</p>
         *
         * @param enabled whether to enable file watching
         * @return this builder
         */
        @NotNull
        public Builder enableFileWatcher(boolean enabled) {
            configuration = configuration.withWatchFiles(enabled);
            return this;
        }

        /**
         * Builds the R18nManager instance.
         *
         * @return a new R18nManager instance
         */
        @NotNull
        public R18nManager build() {
            return new R18nManager(this);
        }
    }
}
