package de.jexcellence.jextranslate.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TranslationService {

    private static final Logger LOGGER = Logger.getLogger(TranslationService.class.getName());
    private static final TranslationKey DEFAULT_PREFIX_KEY = TranslationKey.of("prefix");
    private static final Map<String, Locale> LOCALE_CACHE = new ConcurrentHashMap<>();

    private static volatile ServiceConfiguration configuration;

    private final TranslationKey key;
    private final Player player;
    private final Locale locale;
    private final List<Placeholder> placeholders;
    private final boolean includePrefix;
    private final TranslationKey customPrefixKey;

    private TranslationService(
        @NotNull final TranslationKey key,
        @NotNull final Player player,
        @NotNull final Locale locale,
        @NotNull final List<Placeholder> placeholders,
        final boolean includePrefix,
        @Nullable final TranslationKey customPrefixKey
    ) {
        this.key = key;
        this.player = player;
        this.locale = locale;
        this.placeholders = List.copyOf(placeholders);
        this.includePrefix = includePrefix;
        this.customPrefixKey = customPrefixKey;
    }

    @NotNull
    public static TranslationService create(@NotNull final TranslationKey key, @NotNull final Player player) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");
        ensureConfigured();

        final Locale playerLocale = resolvePlayerLocale(player);
        return new TranslationService(key, player, playerLocale, List.of(), false, null);
    }

    @NotNull
    public static TranslationService create(@NotNull final TranslationKey key, @NotNull final Player player, @NotNull final Locale locale) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");
        ensureConfigured();

        return new TranslationService(key, player, locale, List.of(), false, null);
    }

    @NotNull
    public static TranslationService createFresh(@NotNull final TranslationKey key, @NotNull final Player player) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");
        ensureConfigured();

        clearLocaleCache(player);
        final Locale playerLocale = resolvePlayerLocale(player);
        return new TranslationService(key, player, playerLocale, List.of(), false, null);
    }

    public static void configure(@NotNull final ServiceConfiguration config) {
        configuration = Objects.requireNonNull(config, "Configuration cannot be null");
    }

    @Nullable
    public static ServiceConfiguration getConfiguration() {
        return configuration;
    }

    public static void clearLocaleCache() {
        LOCALE_CACHE.clear();
    }

    public static void clearLocaleCache(@NotNull final Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        final String cacheKey = player.getUniqueId().toString();
        final Locale removed = LOCALE_CACHE.remove(cacheKey);
        if (removed != null) {
            LOGGER.fine("Cleared locale cache for player " + player.getName() + " (was: " + removed + ")");
        }
    }

    private static void ensureConfigured() {
        if (configuration == null) {
            throw new IllegalStateException("TranslationService not configured. Call TranslationService.configure() first.");
        }
    }

    @NotNull
    private static Locale resolvePlayerLocale(@NotNull final Player player) {
        try {
            final String cacheKey = player.getUniqueId().toString();
            return LOCALE_CACHE.computeIfAbsent(cacheKey, key -> {
                final Optional<Locale> playerLocale = configuration.localeResolver().resolveLocale(player);
                return playerLocale.orElseGet(() -> configuration.localeResolver().getDefaultLocale());
            });
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to resolve locale for player " + player.getName(), exception);
            return configuration.repository().getDefaultLocale();
        }
    }

    @NotNull
    public TranslationService withPrefix() {
        return new TranslationService(this.key, this.player, this.locale, this.placeholders, true, null);
    }

    @NotNull
    public TranslationService withPrefix(@NotNull final TranslationKey prefixKey) {
        Objects.requireNonNull(prefixKey, "Prefix key cannot be null");
        return new TranslationService(this.key, this.player, this.locale, this.placeholders, true, prefixKey);
    }

    @NotNull
    public TranslationService with(@NotNull final String key, @Nullable final Object value) {
        Objects.requireNonNull(key, "Placeholder key cannot be null");
        final Placeholder placeholder = createPlaceholder(key, value);
        final List<Placeholder> newPlaceholders = new ArrayList<>(this.placeholders);
        newPlaceholders.add(placeholder);
        return new TranslationService(this.key, this.player, this.locale, newPlaceholders, this.includePrefix, this.customPrefixKey);
    }

    @NotNull
    public TranslationService with(@NotNull final Placeholder placeholder) {
        Objects.requireNonNull(placeholder, "Placeholder cannot be null");
        final List<Placeholder> newPlaceholders = new ArrayList<>(this.placeholders);
        newPlaceholders.add(placeholder);
        return new TranslationService(this.key, this.player, this.locale, newPlaceholders, this.includePrefix, this.customPrefixKey);
    }

    @NotNull
    public TranslationService withAll(@NotNull final Map<String, Object> placeholderMap) {
        Objects.requireNonNull(placeholderMap, "Placeholder map cannot be null");
        final List<Placeholder> newPlaceholders = new ArrayList<>(this.placeholders);
        for (final Map.Entry<String, Object> entry : placeholderMap.entrySet()) {
            final Placeholder placeholder = createPlaceholder(entry.getKey(), entry.getValue());
            newPlaceholders.add(placeholder);
        }
        return new TranslationService(this.key, this.player, this.locale, newPlaceholders, this.includePrefix, this.customPrefixKey);
    }

    @NotNull
    public TranslatedMessage build() {
        try {
            final Optional<String> mainTranslation = configuration.repository().getTranslation(this.key, this.locale);

            if (mainTranslation.isEmpty()) {
                return new TranslatedMessage(Component.text(this.key.key()), this.key);
            }

            final Component mainComponent = configuration.formatter().formatComponent(mainTranslation.get(), this.placeholders, this.locale);

            if (this.includePrefix) {
                final Component prefixComponent = buildPrefixComponent();
                if (prefixComponent != null) {
                    final Component finalComponent = Component.join(JoinConfiguration.noSeparators(), prefixComponent, mainComponent);
                    return new TranslatedMessage(finalComponent, this.key);
                }
            }

            return new TranslatedMessage(mainComponent, this.key);
        } catch (final Exception exception) {
            LOGGER.log(Level.SEVERE, "Unexpected error building message for key: " + this.key, exception);
            return createFallbackMessage(exception);
        }
    }

    @NotNull
    public CompletableFuture<TranslatedMessage> buildAsync() {
        return CompletableFuture.supplyAsync(this::build);
    }

    public void send() {
        this.build().sendTo(this.player);
    }

    public void sendActionBar() {
        this.build().sendActionBar(this.player);
    }

    public void sendTitle() {
        this.build().sendTitle(this.player);
    }

    @NotNull
    private Placeholder createPlaceholder(@NotNull final String key, @Nullable final Object value) {
        if (value == null) {
            return Placeholder.of(key, "");
        }
        if (value instanceof String) {
            return Placeholder.of(key, (String) value);
        }
        if (value instanceof Number) {
            return Placeholder.of(key, (Number) value);
        }
        if (value instanceof Component) {
            return Placeholder.of(key, (Component) value);
        }
        if (value instanceof java.time.LocalDateTime) {
            return Placeholder.of(key, (java.time.LocalDateTime) value);
        }
        return Placeholder.of(key, value.toString());
    }

    @Nullable
    private Component buildPrefixComponent() {
        try {
            final TranslationKey prefixKey = this.customPrefixKey != null ? this.customPrefixKey : DEFAULT_PREFIX_KEY;
            final Optional<String> prefixTranslation = configuration.repository().getTranslation(prefixKey, this.locale);
            return prefixTranslation.map(s -> configuration.formatter().formatComponent(s, this.placeholders, this.locale)).orElse(null);
        } catch (final Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to build prefix component", exception);
            return null;
        }
    }

    @NotNull
    private TranslatedMessage createFallbackMessage(@NotNull final Exception cause) {
        final String fallbackText = String.format("[Error: %s] %s", cause.getClass().getSimpleName(), this.key.key());
        return new TranslatedMessage(Component.text(fallbackText), this.key);
    }

    public record ServiceConfiguration(
        @NotNull TranslationRepository repository,
        @NotNull MessageFormatter formatter,
        @NotNull LocaleResolver localeResolver
    ) {
        public ServiceConfiguration {
            Objects.requireNonNull(repository, "Repository cannot be null");
            Objects.requireNonNull(formatter, "Formatter cannot be null");
            Objects.requireNonNull(localeResolver, "Locale resolver cannot be null");
        }
    }
}
