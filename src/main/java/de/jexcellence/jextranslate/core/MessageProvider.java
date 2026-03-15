package de.jexcellence.jextranslate.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import de.jexcellence.jextranslate.config.R18nConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Advanced message provider with MiniMessage support, legacy fallback, and caching.
 *
 * <p>This class handles the conversion of raw translation strings into formatted
 * Adventure Components or plain strings. It supports both modern MiniMessage
 * formatting and legacy color codes with automatic fallback.</p>
 *
 * <p>When caching is enabled, parsed Components are cached using Caffeine for
 * improved performance. The cache uses a composite key of translation key,
 * locale, and placeholder hash.</p>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class MessageProvider {

    private static final String PREFIX_KEY = "prefix";

    private final R18nConfiguration configuration;
    private final VersionDetector versionDetector;
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    private final PlainTextComponentSerializer plainSerializer;
    private final boolean placeholderAPIAvailable;

    /**
     * Cache for parsed MiniMessage Components.
     * Null when caching is disabled.
     */
    @Nullable
    private final Cache<CacheKey, Component> componentCache;

    /**
     * Metrics for tracking translation usage.
     * Null when metrics are disabled.
     */
    @Nullable
    private final TranslationMetrics metrics;

    private TranslationLoader translationLoader;

    /**
     * Cache key record combining translation key, locale, and placeholder hash.
     *
     * @param key             the translation key
     * @param locale          the locale code
     * @param placeholderHash hash of placeholder values for cache differentiation
     */
    public record CacheKey(@NotNull String key, @NotNull String locale, int placeholderHash) {
        public CacheKey {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(locale, "locale cannot be null");
        }
    }

    /**
     * Creates a provider with optional version detection and runtime feature checks.
     *
     * @param configuration   translation configuration values
     * @param versionDetector detector used to probe optional platform integrations
     */
    public MessageProvider(@NotNull R18nConfiguration configuration, @Nullable VersionDetector versionDetector) {
        this.configuration = configuration;
        this.versionDetector = versionDetector;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacySection();
        this.plainSerializer = PlainTextComponentSerializer.plainText();
        this.placeholderAPIAvailable = configuration.placeholderAPIEnabled() &&
                versionDetector != null &&
                versionDetector.hasClass("me.clip.placeholderapi.PlaceholderAPI");

        // Initialize cache if enabled
        if (configuration.cacheEnabled()) {
            this.componentCache = Caffeine.newBuilder()
                    .maximumSize(configuration.cacheMaxSize())
                    .expireAfterAccess(configuration.cacheExpireMinutes(), TimeUnit.MINUTES)
                    .recordStats()
                    .build();
        } else {
            this.componentCache = null;
        }

        // Initialize metrics if enabled
        this.metrics = configuration.metricsEnabled() ? new TranslationMetrics() : null;
    }

    /**
     * Returns a single merged component for a translation key.
     *
     * @param key          translation key
     * @param locale       locale code used for lookup
     * @param placeholders runtime placeholder values
     * @param includePrefix whether prefix messages should be prepended
     * @return merged component result for the requested key
     */
    @NotNull
    public Component getComponent(@NotNull String key, @NotNull String locale,
                                   @NotNull Map<String, Object> placeholders, boolean includePrefix) {
        // Check cache first if enabled
        if (componentCache != null) {
            int placeholderHash = computePlaceholderHash(placeholders, includePrefix);
            CacheKey cacheKey = new CacheKey(key, locale, placeholderHash);

            Component cached = componentCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }

            // Cache miss - compute and store
            List<Component> components = getComponents(key, locale, placeholders, includePrefix);
            Component result = joinComponents(components);
            
            // Only cache non-empty results (don't cache suppressed messages)
            if (!components.isEmpty()) {
                componentCache.put(cacheKey, result);
            }
            return result;
        }

        // Caching disabled - compute directly
        List<Component> components = getComponents(key, locale, placeholders, includePrefix);
        return joinComponents(components);
    }

    /**
     * Computes a hash for placeholder values to differentiate cached entries.
     *
     * @param placeholders  the placeholder map
     * @param includePrefix whether prefix is included
     * @return hash code for the placeholder combination
     */
    private int computePlaceholderHash(@NotNull Map<String, Object> placeholders, boolean includePrefix) {
        int hash = Boolean.hashCode(includePrefix);
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + Objects.hashCode(entry.getValue());
        }
        return hash;
    }

    /**
     * Returns all rendered components for a translation key.
     *
     * @param key           translation key
     * @param locale        locale code used for lookup
     * @param placeholders  runtime placeholder values
     * @param includePrefix whether prefix messages should be prepended
     * @return ordered component list for the requested key
     */
    @NotNull
    public List<Component> getComponents(@NotNull String key, @NotNull String locale,
                                          @NotNull Map<String, Object> placeholders, boolean includePrefix) {
        List<String> rawMessages = getRawMessages(key, locale, placeholders);
        
        // If no messages (handler returned null to suppress), return empty list
        if (rawMessages.isEmpty()) {
            return List.of();
        }
        
        List<Component> components = new ArrayList<>();

        if (includePrefix) {
            List<String> prefixMessages = getRawMessages(PREFIX_KEY, locale, placeholders);
            for (String prefixMessage : prefixMessages) {
                String processed = processPlaceholders(prefixMessage, placeholders);
                components.add(parseMessage(processed));
            }
        }

        for (String message : rawMessages) {
            String processed = processPlaceholders(message, placeholders);
            components.add(parseMessage(processed));
        }

        return components;
    }

    /**
     * Returns a plain-text representation of a translation key.
     *
     * @param key           translation key
     * @param locale        locale code used for lookup
     * @param placeholders  runtime placeholder values
     * @param includePrefix whether prefix messages should be prepended
     * @return serialized plain-text message
     */
    @NotNull
    public String getString(@NotNull String key, @NotNull String locale,
                            @NotNull Map<String, Object> placeholders, boolean includePrefix) {
        Component component = getComponent(key, locale, placeholders, includePrefix);
        return plainSerializer.serialize(component);
    }

    /**
     * Returns plain-text lines for a translation key.
     *
     * @param key           translation key
     * @param locale        locale code used for lookup
     * @param placeholders  runtime placeholder values
     * @param includePrefix whether prefix messages should be prepended
     * @return plain-text lines for each rendered component
     */
    @NotNull
    public List<String> getStrings(@NotNull String key, @NotNull String locale,
                                    @NotNull Map<String, Object> placeholders, boolean includePrefix) {
        List<Component> components = getComponents(key, locale, placeholders, includePrefix);
        return components.stream().map(plainSerializer::serialize).toList();
    }

    @NotNull
    private List<String> getRawMessages(@NotNull String key, @NotNull String locale,
                                         @NotNull Map<String, Object> placeholders) {
        if (translationLoader == null) {
            recordMissingKeyMetric(key, locale);
            return handleMissingKey(key, locale, placeholders);
        }
        Optional<List<String>> translation = translationLoader.getRawTranslation(key, locale);
        if (translation.isPresent()) {
            recordKeyUsageMetric(key, locale);
            return translation.get();
        }
        recordMissingKeyMetric(key, locale);
        return handleMissingKey(key, locale, placeholders);
    }

    /**
     * Records key usage metric if metrics are enabled.
     *
     * @param key    the translation key
     * @param locale the locale
     */
    private void recordKeyUsageMetric(@NotNull String key, @NotNull String locale) {
        if (metrics != null) {
            metrics.recordKeyUsage(key, locale);
        }
    }

    /**
     * Records missing key metric if metrics are enabled.
     *
     * @param key    the translation key
     * @param locale the locale
     */
    private void recordMissingKeyMetric(@NotNull String key, @NotNull String locale) {
        if (metrics != null) {
            metrics.recordMissingKey(key, locale);
        }
    }

    /**
     * Handles a missing translation key by invoking the configured MissingKeyHandler.
     *
     * @param key          the missing translation key
     * @param locale       the locale that was being used
     * @param placeholders the placeholders that were provided
     * @return a list containing the fallback message, or empty list if handler returns null
     */
    @NotNull
    private List<String> handleMissingKey(@NotNull String key, @NotNull String locale,
                                           @NotNull Map<String, Object> placeholders) {
        R18nConfiguration.MissingKeyHandler handler = configuration.missingKeyHandler();
        String fallbackMessage = handler.handle(key, locale, placeholders);
        
        // If handler returns null, suppress the message entirely
        if (fallbackMessage == null) {
            return List.of();
        }
        
        return List.of(fallbackMessage);
    }

    @NotNull
    private String processPlaceholders(@NotNull String message, @NotNull Map<String, Object> placeholders) {
        String processed = message;

        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());

            if (configuration.legacyColorSupport()) {
                value = convertLegacyColors(value);
            }

            // Escape MiniMessage tags in placeholder values
            value = escapeMiniMessage(value);

            processed = processed.replace("{" + key + "}", value);
            processed = processed.replace("%" + key + "%", value);
        }

        return processed;
    }

    @NotNull
    private Component parseMessage(@NotNull String message) {
        try {
            String converted = configuration.legacyColorSupport() ? convertLegacyColors(message) : message;
            return miniMessage.deserialize(converted);
        } catch (Exception e) {
            return Component.text(message);
        }
    }

    @NotNull
    private String convertLegacyColors(@NotNull String text) {
        if (!configuration.legacyColorSupport()) return text;

        String converted = text.replace("&", "§");
        converted = converted
                .replace("§0", "<black>").replace("§1", "<dark_blue>")
                .replace("§2", "<dark_green>").replace("§3", "<dark_aqua>")
                .replace("§4", "<dark_red>").replace("§5", "<dark_purple>")
                .replace("§6", "<gold>").replace("§7", "<gray>")
                .replace("§8", "<dark_gray>").replace("§9", "<blue>")
                .replace("§a", "<green>").replace("§b", "<aqua>")
                .replace("§c", "<red>").replace("§d", "<light_purple>")
                .replace("§e", "<yellow>").replace("§f", "<white>")
                .replace("§k", "<obfuscated>").replace("§l", "<bold>")
                .replace("§m", "<strikethrough>").replace("§n", "<underlined>")
                .replace("§o", "<italic>").replace("§r", "<reset>");
        return converted;
    }

    @NotNull
    private String escapeMiniMessage(@NotNull String text) {
        return text.replace("<", "\\<").replace(">", "\\>");
    }

    @NotNull
    private Component joinComponents(@NotNull List<Component> components) {
        if (components.isEmpty()) return Component.empty();
        if (components.size() == 1) return components.get(0);

        Component result = components.get(0);
        for (int i = 1; i < components.size(); i++) {
            result = result.appendNewline().append(components.get(i));
        }
        return result;
    }

    /**
     * Sets translationLoader.
     */
    public void setTranslationLoader(@NotNull TranslationLoader translationLoader) {
        this.translationLoader = translationLoader;
    }

    /**
     * Returns whether placeholderAPIAvailable.
     */
    public boolean isPlaceholderAPIAvailable() {
        return placeholderAPIAvailable;
    }

    /**
     * Gets versionDetector.
     */
    @NotNull
    public VersionDetector getVersionDetector() {
        return versionDetector;
    }

    /**
     * Gets configuration.
     */
    @NotNull
    public R18nConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Invalidates all cached translations.
     * Should be called when translations are reloaded.
     */
    public void invalidateCache() {
        if (componentCache != null) {
            componentCache.invalidateAll();
        }
    }

    /**
     * Gets cache statistics for monitoring.
     *
     * @return cache statistics, or null if caching is disabled
     */
    @Nullable
    public CacheStats getCacheStats() {
        return componentCache != null ? componentCache.stats() : null;
    }

    /**
     * Checks if caching is enabled.
     *
     * @return true if caching is enabled
     */
    public boolean isCacheEnabled() {
        return componentCache != null;
    }

    /**
     * Gets the translation metrics.
     *
     * @return the metrics instance, or null if metrics are disabled
     */
    @Nullable
    public TranslationMetrics getMetrics() {
        return metrics;
    }

    /**
     * Checks if metrics collection is enabled.
     *
     * @return true if metrics are enabled
     */
    public boolean isMetricsEnabled() {
        return metrics != null;
    }
}
