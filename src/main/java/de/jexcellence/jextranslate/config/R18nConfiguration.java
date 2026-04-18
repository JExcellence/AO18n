

package de.jexcellence.jextranslate.config;

import de.jexcellence.jextranslate.bedrock.BedrockFormatMode;
import de.jexcellence.jextranslate.bedrock.HexColorFallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable configuration record for R18n settings.
 *
 * <p>This record holds all configuration options for the R18n system.
 * It uses the builder pattern through immutable methods that return
 * new instances with modified values.</p>
 *
 * <p><strong>Configuration Options:</strong></p>
 * <ul>
 *   <li><strong>defaultLocale</strong> - The fallback locale when translations are missing (e.g., "en_GB")</li>
 *   <li><strong>supportedLocales</strong> - Set of locales that have translation files</li>
 *   <li><strong>translationDirectory</strong> - Directory name containing translation files</li>
 *   <li><strong>keyValidationEnabled</strong> - Whether to validate translation keys on load</li>
 *   <li><strong>placeholderAPIEnabled</strong> - Whether to integrate with PlaceholderAPI</li>
 *   <li><strong>legacyColorSupport</strong> - Whether to support legacy color codes (§ and &amp;)</li>
 *   <li><strong>debugMode</strong> - Whether to enable debug logging</li>
 *   <li><strong>cacheEnabled</strong> - Whether to cache parsed MiniMessage Components</li>
 *   <li><strong>cacheMaxSize</strong> - Maximum number of entries in the translation cache</li>
 *   <li><strong>cacheExpireMinutes</strong> - Cache entry expiration time in minutes</li>
 *   <li><strong>watchFiles</strong> - Whether to watch translation files for changes</li>
 *   <li><strong>metricsEnabled</strong> - Whether to collect translation usage metrics</li>
 *   <li><strong>missingKeyHandler</strong> - Custom handler for missing translation keys</li>
 *   <li><strong>bedrockSupportEnabled</strong> - Whether Bedrock Edition player support is enabled (default: true)</li>
 *   <li><strong>hexColorFallback</strong> - How hex colors are handled for Bedrock players (default: NEAREST_LEGACY)</li>
 *   <li><strong>bedrockFormatMode</strong> - Formatting mode for Bedrock players (default: CONSERVATIVE)</li>
 * </ul>
 *
 * @param defaultLocale         the default locale code (e.g., "en_GB")
 * @param supportedLocales      set of supported locale codes
 * @param translationDirectory  directory name for translation files
 * @param keyValidationEnabled  whether key validation is enabled
 * @param placeholderAPIEnabled whether PlaceholderAPI integration is enabled
 * @param legacyColorSupport    whether legacy color codes are supported
 * @param debugMode             whether debug mode is enabled
 * @param cacheEnabled          whether translation caching is enabled
 * @param cacheMaxSize          maximum cache size
 * @param cacheExpireMinutes    cache expiration time in minutes
 * @param watchFiles            whether file watching is enabled
 * @param metricsEnabled        whether metrics collection is enabled
 * @param missingKeyHandler     handler for missing translation keys
 * @param bedrockSupportEnabled whether Bedrock Edition player support is enabled
 * @param hexColorFallback      how hex colors are handled for Bedrock players
 * @param bedrockFormatMode     formatting mode for Bedrock players
 * @author JExcellence
 * @version 3.0.0
 * @since 2.0.0
 */
public record R18nConfiguration(
        @NotNull String defaultLocale,
        @NotNull Set<String> supportedLocales,
        @NotNull String translationDirectory,
        boolean keyValidationEnabled,
        boolean placeholderAPIEnabled,
        boolean legacyColorSupport,
        boolean debugMode,
        boolean cacheEnabled,
        int cacheMaxSize,
        int cacheExpireMinutes,
        boolean watchFiles,
        boolean metricsEnabled,
        @NotNull MissingKeyHandler missingKeyHandler,
        boolean bedrockSupportEnabled,
        @NotNull HexColorFallback hexColorFallback,
        @NotNull BedrockFormatMode bedrockFormatMode
) {

    /**
     * Functional interface for handling missing translation keys.
     *
     * <p>This interface allows customization of what happens when a translation key
     * is not found. The handler receives the key, locale, and placeholders, and can
     * return a custom message or null to suppress the message entirely.</p>
     */
    @FunctionalInterface
    public interface MissingKeyHandler {
        /**
         * Handles a missing translation key.
         *
         * @param key          the missing translation key
         * @param locale       the locale that was being used
         * @param placeholders the placeholders that were provided
         * @return a fallback message, or null to suppress the message
         */
        @Nullable
        String handle(@NotNull String key, @NotNull String locale, @NotNull Map<String, Object> placeholders);
    }

    /**
     * Default missing key handler that returns a formatted message showing the missing key.
     */
    public static final MissingKeyHandler DEFAULT_MISSING_KEY_HANDLER =
            (key, locale, placeholders) -> "<gold>Missing: <red>" + key + "</red></gold>";

    /**
     * Special marker set indicating auto-detection of locales from translation files.
     * When supportedLocales equals this set, all translation files will be loaded regardless of locale.
     */
    public static final Set<String> AUTO_DETECT_LOCALES = Set.of("*");

    /**
     * Creates a new configuration with validation.
     */
    public R18nConfiguration {
        if (defaultLocale == null || defaultLocale.trim().isEmpty()) {
            throw new IllegalArgumentException("Default locale cannot be null or empty");
        }
        if (supportedLocales == null) {
            throw new IllegalArgumentException("Supported locales cannot be null");
        }
        // Allow empty supportedLocales for auto-detection mode
        if (!supportedLocales.isEmpty() && !supportedLocales.equals(AUTO_DETECT_LOCALES) && !supportedLocales.contains(defaultLocale)) {
            throw new IllegalArgumentException("Default locale must be in supported locales (or use empty set for auto-detection)");
        }
        if (translationDirectory == null || translationDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Translation directory cannot be null or empty");
        }
        if (cacheMaxSize < 0) {
            throw new IllegalArgumentException("Cache max size cannot be negative");
        }
        if (cacheExpireMinutes < 0) {
            throw new IllegalArgumentException("Cache expire minutes cannot be negative");
        }
        if (missingKeyHandler == null) {
            throw new IllegalArgumentException("Missing key handler cannot be null");
        }
        if (hexColorFallback == null) {
            throw new IllegalArgumentException("Hex color fallback cannot be null");
        }
        if (bedrockFormatMode == null) {
            throw new IllegalArgumentException("Bedrock format mode cannot be null");
        }
        // Make defensive copies
        supportedLocales = Collections.unmodifiableSet(new HashSet<>(supportedLocales));
        defaultLocale = defaultLocale.trim();
        translationDirectory = translationDirectory.trim();
    }

    /**
     * Creates a default configuration.
     *
     * @return a default configuration instance
     */
    @NotNull
    public static R18nConfiguration defaultConfiguration() {
        return new R18nConfiguration(
                "en_US",
                Set.of("en_US"),
                "translations",
                true,
                false,
                true,
                false,
                true,
                1000,
                30,
                false,
                false,
                DEFAULT_MISSING_KEY_HANDLER,
                true,
                HexColorFallback.NEAREST_LEGACY,
                BedrockFormatMode.CONSERVATIVE
        );
    }

    // ── Immutable "with" modifiers ─────────────────────────────────────────────
    // Each method returns a new instance with one field changed, delegating to the Builder
    // to avoid repeating all 16 constructor parameters.

    /** Returns a copy with the given default locale (also adds it to supported locales). */
    @NotNull public R18nConfiguration withDefaultLocale(@NotNull String defaultLocale)            { return toBuilder().defaultLocale(defaultLocale).build(); }
    /** Returns a copy with auto-detection of locales (loads every file found). */
    @NotNull public R18nConfiguration withAutoDetectLocales()                                     { return toBuilder().autoDetectLocales().build(); }
    /** Returns a copy with the given supported locales. */
    @NotNull public R18nConfiguration withSupportedLocales(@NotNull String... locales)            { return toBuilder().supportedLocales(locales).build(); }
    /** Returns a copy with the given supported locales. */
    @NotNull public R18nConfiguration withSupportedLocales(@NotNull Set<String> supportedLocales) { return toBuilder().supportedLocales(supportedLocales).build(); }
    /** Returns a copy with the given translation directory. */
    @NotNull public R18nConfiguration withTranslationDirectory(@NotNull String dir)               { return toBuilder().translationDirectory(dir).build(); }
    /** Returns a copy with key validation enabled/disabled. */
    @NotNull public R18nConfiguration withKeyValidationEnabled(boolean enabled)                   { return toBuilder().keyValidationEnabled(enabled).build(); }
    /** Returns a copy with PlaceholderAPI integration enabled/disabled. */
    @NotNull public R18nConfiguration withPlaceholderAPIEnabled(boolean enabled)                  { return toBuilder().placeholderAPIEnabled(enabled).build(); }
    /** Returns a copy with legacy colour support enabled/disabled. */
    @NotNull public R18nConfiguration withLegacyColorSupport(boolean enabled)                     { return toBuilder().legacyColorSupport(enabled).build(); }
    /** Returns a copy with debug mode enabled/disabled. */
    @NotNull public R18nConfiguration withDebugMode(boolean enabled)                              { return toBuilder().debugMode(enabled).build(); }
    /** Returns a copy with caching enabled/disabled. */
    @NotNull public R18nConfiguration withCacheEnabled(boolean enabled)                           { return toBuilder().enableCache(enabled).build(); }
    /** Returns a copy with the given cache max size. */
    @NotNull public R18nConfiguration withCacheMaxSize(int maxSize)                               { return toBuilder().cacheMaxSize(maxSize).build(); }
    /** Returns a copy with the given cache expiration (minutes). */
    @NotNull public R18nConfiguration withCacheExpireMinutes(int minutes)                         { return toBuilder().cacheExpireMinutes(minutes).build(); }
    /** Returns a copy with file watching enabled/disabled. */
    @NotNull public R18nConfiguration withWatchFiles(boolean enabled)                             { return toBuilder().enableFileWatcher(enabled).build(); }
    /** Returns a copy with metrics collection enabled/disabled. */
    @NotNull public R18nConfiguration withMetricsEnabled(boolean enabled)                         { return toBuilder().enableMetrics(enabled).build(); }
    /** Returns a copy with the given missing key handler. */
    @NotNull public R18nConfiguration withMissingKeyHandler(@NotNull MissingKeyHandler handler)   { return toBuilder().onMissingKey(handler).build(); }
    /** Returns a copy with Bedrock support enabled/disabled. */
    @NotNull public R18nConfiguration withBedrockSupportEnabled(boolean enabled)                  { return toBuilder().bedrockSupportEnabled(enabled).build(); }
    /** Returns a copy with the given hex colour fallback strategy. */
    @NotNull public R18nConfiguration withHexColorFallback(@NotNull HexColorFallback fallback)    { return toBuilder().hexColorFallback(fallback).build(); }
    /** Returns a copy with the given Bedrock format mode. */
    @NotNull public R18nConfiguration withBedrockFormatMode(@NotNull BedrockFormatMode mode)      { return toBuilder().bedrockFormatMode(mode).build(); }

    /**
     * Checks if a locale is supported.
     *
     * @param locale the locale to check
     * @return true if the locale is supported
     */
    public boolean isLocaleSupported(@NotNull String locale) {
        return supportedLocales.contains(locale);
    }

    /**
     * Gets the best matching locale for the given locale string.
     * This method tries exact match first, then language part only.
     *
     * @param locale the requested locale
     * @return the best matching supported locale, or default locale if no match
     */
    @NotNull
    public String getBestMatchingLocale(@NotNull String locale) {
        // Exact match
        if (supportedLocales.contains(locale)) {
            return locale;
        }
        // Try language part only (e.g., "en" from "en_GB")
        String language = locale.split("[_-]")[0];
        if (supportedLocales.contains(language)) {
            return language;
        }
        // Try finding any locale starting with the language
        for (String supported : supportedLocales) {
            if (supported.startsWith(language + "_") || supported.startsWith(language + "-")) {
                return supported;
            }
        }
        // Fallback to default
        return defaultLocale;
    }

    /**
     * Gets the default locale.
     *
     * @return the default locale
     */
    @NotNull
    public String getDefaultLocale() {
        return defaultLocale;
    }

    /**
     * Gets the supported locales.
     *
     * @return an unmodifiable set of supported locales
     */
    @NotNull
    public Set<String> getSupportedLocales() {
        return supportedLocales;
    }

    /**
     * Creates a builder from this configuration.
     *
     * @return a new builder with this configuration's values
     */
    @NotNull
    public Builder toBuilder() {
        return new Builder()
                .defaultLocale(defaultLocale)
                .supportedLocales(supportedLocales)
                .translationDirectory(translationDirectory)
                .keyValidationEnabled(keyValidationEnabled)
                .placeholderAPIEnabled(placeholderAPIEnabled)
                .legacyColorSupport(legacyColorSupport)
                .debugMode(debugMode)
                .enableCache(cacheEnabled)
                .cacheMaxSize(cacheMaxSize)
                .cacheExpireMinutes(cacheExpireMinutes)
                .enableFileWatcher(watchFiles)
                .enableMetrics(metricsEnabled)
                .onMissingKey(missingKeyHandler)
                .bedrockSupportEnabled(bedrockSupportEnabled)
                .hexColorFallback(hexColorFallback)
                .bedrockFormatMode(bedrockFormatMode);
    }

    /**
     * Builder for creating R18nConfiguration instances.
     */
    public static final class Builder {
        private String defaultLocale = "en_US";
        private Set<String> supportedLocales = new HashSet<>(Set.of("en_US"));
        private String translationDirectory = "translations";
        private boolean keyValidationEnabled = true;
        private boolean placeholderAPIEnabled = false;
        private boolean legacyColorSupport = true;
        private boolean debugMode = false;
        private boolean cacheEnabled = true;
        private int cacheMaxSize = 1000;
        private int cacheExpireMinutes = 30;
        private boolean watchFiles = false;
        private boolean metricsEnabled = false;
        private MissingKeyHandler missingKeyHandler = DEFAULT_MISSING_KEY_HANDLER;
        private boolean bedrockSupportEnabled = true;
        private HexColorFallback hexColorFallback = HexColorFallback.NEAREST_LEGACY;
        private BedrockFormatMode bedrockFormatMode = BedrockFormatMode.CONSERVATIVE;

        /**
         * Sets the default locale.
         *
         * @param defaultLocale the default locale
         * @return this builder
         */
        @NotNull
        public Builder defaultLocale(@NotNull String defaultLocale) {
            this.defaultLocale = defaultLocale;
            this.supportedLocales.add(defaultLocale);
            return this;
        }

        /**
         * Enables auto-detection of locales from translation files.
         * All files in the translation directory will be loaded regardless of locale code.
         *
         * @return this builder
         */
        @NotNull
        public Builder autoDetectLocales() {
            this.supportedLocales = new HashSet<>();
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
            return supportedLocales(Set.of(locales));
        }

        /**
         * Sets the supported locales.
         *
         * @param supportedLocales the supported locales
         * @return this builder
         */
        @NotNull
        public Builder supportedLocales(@NotNull Set<String> supportedLocales) {
            this.supportedLocales = new HashSet<>(supportedLocales);
            this.supportedLocales.add(defaultLocale);
            return this;
        }

        /**
         * Sets the translation directory.
         *
         * @param translationDirectory the translation directory
         * @return this builder
         */
        @NotNull
        public Builder translationDirectory(@NotNull String translationDirectory) {
            this.translationDirectory = translationDirectory;
            return this;
        }

        /**
         * Sets key validation enabled.
         *
         * @param enabled whether key validation is enabled
         * @return this builder
         */
        @NotNull
        public Builder keyValidationEnabled(boolean enabled) {
            this.keyValidationEnabled = enabled;
            return this;
        }

        /**
         * Sets PlaceholderAPI integration enabled.
         *
         * @param enabled whether PlaceholderAPI integration is enabled
         * @return this builder
         */
        @NotNull
        public Builder placeholderAPIEnabled(boolean enabled) {
            this.placeholderAPIEnabled = enabled;
            return this;
        }

        /**
         * Sets legacy color support enabled.
         *
         * @param enabled whether legacy color support is enabled
         * @return this builder
         */
        @NotNull
        public Builder legacyColorSupport(boolean enabled) {
            this.legacyColorSupport = enabled;
            return this;
        }

        /**
         * Sets debug mode enabled.
         *
         * @param enabled whether debug mode is enabled
         * @return this builder
         */
        @NotNull
        public Builder debugMode(boolean enabled) {
            this.debugMode = enabled;
            return this;
        }

        /**
         * Enables or disables translation caching.
         *
         * @param enabled whether caching is enabled
         * @return this builder
         */
        @NotNull
        public Builder enableCache(boolean enabled) {
            this.cacheEnabled = enabled;
            return this;
        }

        /**
         * Sets the maximum cache size.
         *
         * @param maxSize the maximum number of cached entries
         * @return this builder
         */
        @NotNull
        public Builder cacheMaxSize(int maxSize) {
            this.cacheMaxSize = maxSize;
            return this;
        }

        /**
         * Sets the cache expiration time in minutes.
         *
         * @param minutes the expiration time in minutes
         * @return this builder
         */
        @NotNull
        public Builder cacheExpireMinutes(int minutes) {
            this.cacheExpireMinutes = minutes;
            return this;
        }

        /**
         * Enables or disables file watching for hot reload.
         *
         * @param enabled whether file watching is enabled
         * @return this builder
         */
        @NotNull
        public Builder enableFileWatcher(boolean enabled) {
            this.watchFiles = enabled;
            return this;
        }

        /**
         * Enables or disables translation metrics collection.
         *
         * @param enabled whether metrics collection is enabled
         * @return this builder
         */
        @NotNull
        public Builder enableMetrics(boolean enabled) {
            this.metricsEnabled = enabled;
            return this;
        }

        /**
         * Sets a custom handler for missing translation keys.
         *
         * @param handler the missing key handler
         * @return this builder
         */
        @NotNull
        public Builder onMissingKey(@NotNull MissingKeyHandler handler) {
            this.missingKeyHandler = handler;
            return this;
        }

        /**
         * Enables or disables Bedrock Edition player support.
         *
         * @param enabled whether Bedrock support is enabled
         * @return this builder
         */
        @NotNull
        public Builder bedrockSupportEnabled(boolean enabled) {
            this.bedrockSupportEnabled = enabled;
            return this;
        }

        /**
         * Sets the hex color fallback strategy for Bedrock players.
         *
         * @param fallback the hex color fallback strategy
         * @return this builder
         */
        @NotNull
        public Builder hexColorFallback(@NotNull HexColorFallback fallback) {
            this.hexColorFallback = fallback;
            return this;
        }

        /**
         * Sets the Bedrock format mode.
         *
         * @param mode the Bedrock format mode
         * @return this builder
         */
        @NotNull
        public Builder bedrockFormatMode(@NotNull BedrockFormatMode mode) {
            this.bedrockFormatMode = mode;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return a new R18nConfiguration instance
         */
        @NotNull
        public R18nConfiguration build() {
            return new R18nConfiguration(
                    defaultLocale,
                    supportedLocales,
                    translationDirectory,
                    keyValidationEnabled,
                    placeholderAPIEnabled,
                    legacyColorSupport,
                    debugMode,
                    cacheEnabled,
                    cacheMaxSize,
                    cacheExpireMinutes,
                    watchFiles,
                    metricsEnabled,
                    missingKeyHandler,
                    bedrockSupportEnabled,
                    hexColorFallback,
                    bedrockFormatMode
            );
        }
    }
}
