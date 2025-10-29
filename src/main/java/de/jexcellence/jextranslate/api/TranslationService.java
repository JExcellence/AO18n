package de.jexcellence.jextranslate.api;

import de.jexcellence.jextranslate.util.TranslationLogger;
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

/**
 * Fluent facade responsible for resolving translation templates, applying placeholders, and dispatching
 * {@link TranslatedMessage} instances to Bukkit players.
 * <p>
 * Every call chain <strong>must</strong> be preceded by a repository bootstrap via {@link #configure(ServiceConfiguration)}.
 * The configuration wires the {@link TranslationRepository repository}, {@link MessageFormatter formatter}, and
 * {@link LocaleResolver resolver} that drive locale detection and MiniMessage placeholder handling for the entire
 * module. Locale lookups follow the cascade <em>explicit override → stored player locale → resolver default →
 * repository default</em> and resolved values are cached per-player until {@link #clearLocaleCache()} or
 * {@link #clearLocaleCache(Player)} is invoked.
 * <p>
 * Prefix rendering expects MiniMessage-compatible markup; placeholders supplied through {@link #with(String, Object)}
 * and its overloads are expanded with the configured {@link MessageFormatter}, ensuring repository, formatter, and
 * resolver components remain synchronized.
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.2
 */
public class TranslationService {

    private static final Logger LOGGER = TranslationLogger.getLogger(TranslationService.class);
    private static final TranslationKey DEFAULT_PREFIX_KEY = TranslationKey.of("prefix");
    private static final Map<String, Locale> LOCALE_CACHE = new ConcurrentHashMap<>();

    private static volatile ServiceConfiguration configuration;
    private static volatile boolean debugLoggingEnabled;

    private final TranslationKey key;
    private final Player player;
    private final Locale locale;
    private final List<Placeholder> placeholders;
    private final boolean withPrefix;
    private final TranslationKey customPrefixKey;

    private TranslationService(
        @NotNull final TranslationKey key,
        @NotNull final Player player,
        @NotNull final Locale locale,
        @NotNull final List<Placeholder> placeholders,
        final boolean withPrefix,
        @Nullable final TranslationKey customPrefixKey
    ) {
        this.key = key;
        this.player = player;
        this.locale = locale;
        this.placeholders = List.copyOf(placeholders);
        this.withPrefix = withPrefix;
        this.customPrefixKey = customPrefixKey;
    }

    /**
     * Creates a new service builder for the supplied translation key, resolving the player's locale via the configured
     * {@link LocaleResolver}. The resolver integrates with {@link TranslationRepository} defaults when no player locale
     * can be resolved and the result is cached for subsequent lookups.
     *
     * @param key    the translation key to resolve
     * @param player the target player that provides locale context and message recipients
     * @return a ready-to-use {@link TranslationService} instance bound to the resolved locale
     * @throws IllegalStateException if {@link #configure(ServiceConfiguration)} has not been called prior to this invocation
     */
    @NotNull
    public static TranslationService create(@NotNull final TranslationKey key, @NotNull final Player player) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");
        ensureConfigured();

        final Locale playerLocale = resolvePlayerLocale(player);
        return new TranslationService(key, player, playerLocale, List.of(), false, null);
    }

    /**
     * Creates a new service builder that bypasses resolver logic by using a caller-supplied locale. This is useful for
     * system broadcasts or diagnostics where the consumer controls language resolution.
     *
     * @param key    the translation key to resolve
     * @param player the player context used for prefix resolution and message dispatch
     * @param locale the locale that should be used without consulting the {@link LocaleResolver}
     * @return a {@link TranslationService} instance bound to the supplied locale
     * @throws IllegalStateException if {@link #configure(ServiceConfiguration)} has not been called prior to this invocation
     */
    @NotNull
    public static TranslationService create(@NotNull final TranslationKey key, @NotNull final Player player, @NotNull final Locale locale) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");
        ensureConfigured();

        return new TranslationService(key, player, locale, List.of(), false, null);
    }

    /**
     * Creates a new service builder after clearing any cached locale entry for the supplied player. Use this when
     * configuration changes occur (e.g., repository reload or locale override) to guarantee resolver values are
     * refreshed before formatting.
     *
     * @param key    the translation key to resolve
     * @param player the player whose cached locale should be invalidated before resolution
     * @return a {@link TranslationService} bound to the freshly resolved locale
     * @throws IllegalStateException if {@link #configure(ServiceConfiguration)} has not been called prior to this invocation
     */
    @NotNull
    public static TranslationService createFresh(@NotNull final TranslationKey key, @NotNull final Player player) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");
        ensureConfigured();

        clearLocaleCache(player);
        final Locale playerLocale = resolvePlayerLocale(player);
        return new TranslationService(key, player, playerLocale, List.of(), false, null);
    }

    /**
     * Bootstraps the translation service with the repository, formatter, and resolver trio used by all subsequent
     * translation operations. This must be invoked during plugin startup (see {@code TranslationService.configure(...)} in
     * {@link de.jexcellence.jextranslate.example.ExamplePlugin}) before accessing any fluent builder methods.
     *
     * @param config the configuration object linking repository, formatter, and resolver components
     */
    public static void configure(@NotNull final ServiceConfiguration config) {
        configuration = Objects.requireNonNull(config, "Configuration cannot be null");
    }

    /**
     * Retrieves the currently active service configuration.
     *
     * @return the current {@link ServiceConfiguration}, or {@code null} if {@link #configure(ServiceConfiguration)} has not been invoked yet
     */
    @Nullable
    public static ServiceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Enables verbose debug logging for locale resolution and cache invalidation events.
     */
    public static void enableDebugLogging() {
        debugLoggingEnabled = true;
    }

    /**
     * Disables verbose debug logging previously enabled via {@link #enableDebugLogging()}.
     */
    public static void disableDebugLogging() {
        debugLoggingEnabled = false;
    }

    /**
     * Indicates whether debug logging is active.
     *
     * @return {@code true} when debug logging is enabled
     */
    public static boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled;
    }

    /**
     * Clears every cached locale entry. This should be triggered whenever the {@link LocaleResolver} state changes
     * (for example via in-game locale commands) or when the backing {@link TranslationRepository} is reloaded.
     */
    public static void clearLocaleCache() {
        LOCALE_CACHE.clear();
    }

    /**
     * Clears the cached locale entry for a single player, forcing the next lookup to consult the configured
     * {@link LocaleResolver}. This is typically paired with resolver overrides for individuals.
     *
     * @param player the player whose cached locale should be invalidated
     */
    public static void clearLocaleCache(@NotNull final Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        final String cacheKey = player.getUniqueId().toString();
        final Locale removed = LOCALE_CACHE.remove(cacheKey);
        if (removed != null) {
            debug("Cleared locale cache for player", Map.of(
                    "player", TranslationLogger.anonymize(player.getUniqueId()),
                    "previousLocale", removed.toString()
            ));
        }
    }

    /**
     * Ensures the service has been configured prior to use.
     *
     * @throws IllegalStateException if configuration has not yet been supplied
     */
    private static void ensureConfigured() {
        if (configuration == null) {
            throw new IllegalStateException("TranslationService not configured. Call TranslationService.configure() first.");
        }
    }

    /**
     * Resolves the locale for the supplied player while maintaining the local cache. Resolution consults the
     * {@link LocaleResolver} first, falling back to the resolver default and finally the {@link TranslationRepository}
     * default locale when errors occur.
     *
     * @param player the player whose locale should be resolved
     * @return the locale used for translation lookups and formatting
     */
    @NotNull
    private static Locale resolvePlayerLocale(@NotNull final Player player) {
        try {
            final String cacheKey = player.getUniqueId().toString();
            return LOCALE_CACHE.computeIfAbsent(cacheKey, key -> {
                final Optional<Locale> playerLocale = configuration.localeResolver().resolveLocale(player);
                final Locale resolved = playerLocale.orElseGet(() -> configuration.localeResolver().getDefaultLocale());
                debug("Resolved player locale", Map.of(
                        "player", TranslationLogger.anonymize(player.getUniqueId()),
                        "locale", resolved.toString()
                ));
                return resolved;
            });
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to resolve player locale",
                            Map.of("player", TranslationLogger.anonymize(player.getUniqueId()))
                    ),
                    exception
            );
            return configuration.repository().getDefaultLocale();
        }
    }

    /**
     * Returns a new builder instance that will prepend the default prefix before the resolved message body. The prefix
     * is fetched through the configured {@link TranslationRepository} using {@code prefix} as the translation key.
     *
     * @return a new {@link TranslationService} configured to include the default prefix
     */
    @NotNull
    public TranslationService withPrefix() {
        return new TranslationService(this.key, this.player, this.locale, this.placeholders, true, null);
    }

    /**
     * Returns a new builder instance that will prepend a custom prefix translation before the message body.
     *
     * @param prefixKey the translation key that should provide the prefix component
     * @return a new {@link TranslationService} configured to include the specified prefix
     */
    @NotNull
    public TranslationService withPrefix(@NotNull final TranslationKey prefixKey) {
        Objects.requireNonNull(prefixKey, "Prefix key cannot be null");
        return new TranslationService(this.key, this.player, this.locale, this.placeholders, true, prefixKey);
    }

    /**
     * Adds a placeholder to the builder by key and value, automatically selecting the appropriate
     * {@link Placeholder} implementation based on the provided value type. MiniMessage placeholders must be wrapped in
     * braces (e.g., {@code {player}}) within the translation template.
     *
     * @param key   the placeholder key without surrounding braces
     * @param value the value to substitute; supports strings, numbers, {@link Component components}, and temporal values
     * @return a new {@link TranslationService} instance that includes the supplied placeholder
     */
    @NotNull
    public TranslationService with(@NotNull final String key, @Nullable final Object value) {
        Objects.requireNonNull(key, "Placeholder key cannot be null");
        final Placeholder placeholder = createPlaceholder(key, value);
        final List<Placeholder> newPlaceholders = new ArrayList<>(this.placeholders);
        newPlaceholders.add(placeholder);
        return new TranslationService(this.key, this.player, this.locale, newPlaceholders, this.withPrefix, this.customPrefixKey);
    }

    /**
     * Adds the supplied placeholder to the builder without modification. Use this overload when constructing
     * placeholders manually to control formatting behaviour.
     *
     * @param placeholder the placeholder to append to the builder
     * @return a new {@link TranslationService} that includes the supplied placeholder
     */
    @NotNull
    public TranslationService with(@NotNull final Placeholder placeholder) {
        Objects.requireNonNull(placeholder, "Placeholder cannot be null");
        final List<Placeholder> newPlaceholders = new ArrayList<>(this.placeholders);
        newPlaceholders.add(placeholder);
        return new TranslationService(this.key, this.player, this.locale, newPlaceholders, this.withPrefix, this.customPrefixKey);
    }

    /**
     * Adds every entry from the supplied map as placeholders. Values are automatically converted using
     * {@link #createPlaceholder(String, Object)}, ensuring MiniMessage placeholder expectations remain consistent with
     * the configured {@link MessageFormatter}.
     *
     * @param placeholderMap map of placeholder keys (without braces) to arbitrary values
     * @return a new {@link TranslationService} populated with the provided placeholders
     */
    @NotNull
    public TranslationService withAll(@NotNull final Map<String, Object> placeholderMap) {
        Objects.requireNonNull(placeholderMap, "Placeholder map cannot be null");
        final List<Placeholder> newPlaceholders = new ArrayList<>(this.placeholders);
        for (final Map.Entry<String, Object> entry : placeholderMap.entrySet()) {
            final Placeholder placeholder = createPlaceholder(entry.getKey(), entry.getValue());
            newPlaceholders.add(placeholder);
        }
        return new TranslationService(this.key, this.player, this.locale, newPlaceholders, this.withPrefix, this.customPrefixKey);
    }

    /**
     * Builds the {@link TranslatedMessage} by fetching the template from the {@link TranslationRepository}, formatting it
     * with the configured {@link MessageFormatter}, and prepending any prefix component. Failures fall back to a plain
     * text component containing the key and error details.
     *
     * @return the rendered {@link TranslatedMessage}
     */
    @NotNull
    public TranslatedMessage build() {
        try {
            final Optional<String> mainTranslation = configuration.repository().getTranslation(this.key, this.locale);

            if (mainTranslation.isEmpty()) {
                return new TranslatedMessage(Component.text(this.key.key()), this.key);
            }

            final Component mainComponent = configuration.formatter().formatComponent(mainTranslation.get(), this.placeholders, this.locale);

            if (this.withPrefix) {
                final Component prefixComponent = buildPrefixComponent();
                if (prefixComponent != null) {
                    final Component finalComponent = Component.join(JoinConfiguration.noSeparators(), prefixComponent, mainComponent);
                    return new TranslatedMessage(finalComponent, this.key);
                }
            }

            return new TranslatedMessage(mainComponent, this.key);
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.SEVERE,
                    TranslationLogger.message(
                            "Unexpected error building translated message",
                            Map.of("key", this.key.key())
                    ),
                    exception
            );
            return createFallbackMessage(exception);
        }
    }

    /**
     * Asynchronously builds the translated message using {@link CompletableFuture#supplyAsync}. Useful for expensive
     * placeholder preparation or repository access occurring off the primary server thread.
     *
     * @return a future that completes with the rendered {@link TranslatedMessage}
     */
    @NotNull
    public CompletableFuture<TranslatedMessage> buildAsync() {
        return CompletableFuture.supplyAsync(this::build);
    }

    /**
     * Convenience method that builds and immediately sends the translation to the associated player via
     * {@link TranslatedMessage#sendTo(Player)}.
     */
    public void send() {
        this.build().sendTo(this.player);
    }

    /**
     * Convenience method that builds and dispatches the translation as an action bar message.
     */
    public void sendActionBar() {
        this.build().sendActionBar(this.player);
    }

    /**
     * Convenience method that builds and dispatches the translation as a title using default fade durations.
     */
    public void sendTitle() {
        this.build().sendTitle(this.player);
    }

    /**
     * Creates an appropriate {@link Placeholder} instance for the given key and value. MiniMessage placeholders should
     * be referenced using brace syntax in templates. Null values are converted to empty strings to avoid literal
     * {@code null} output.
     *
     * @param key   the placeholder key without braces
     * @param value the placeholder value, potentially {@code null}
     * @return a concrete {@link Placeholder} implementation suitable for the value type
     */
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

    /**
     * Builds the prefix component when {@link #withPrefix()} or {@link #withPrefix(TranslationKey)} has been invoked.
     * Missing prefixes are ignored so that the main component can still be delivered.
     *
     * @return the prefix component or {@code null} when the prefix translation is unavailable
     */
    @Nullable
    private Component buildPrefixComponent() {
        final TranslationKey prefixKey = this.customPrefixKey != null ? this.customPrefixKey : DEFAULT_PREFIX_KEY;
        try {
            final Optional<String> prefixTranslation = configuration.repository().getTranslation(prefixKey, this.locale);
            return prefixTranslation.map(s -> configuration.formatter().formatComponent(s, this.placeholders, this.locale)).orElse(null);
        } catch (final Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    TranslationLogger.message(
                            "Failed to build prefix component",
                            Map.of(
                                    "key", this.key.key(),
                                    "prefixKey", prefixKey.key()
                            )
                    ),
                    exception
            );
            return null;
        }
    }

    /**
     * Creates a fallback message containing contextual error information to aid debugging when formatting fails.
     *
     * @param cause the exception that triggered the fallback
     * @return a {@link TranslatedMessage} describing the error
     */
    @NotNull
    private TranslatedMessage createFallbackMessage(@NotNull final Exception cause) {
        final String fallbackText = String.format("[Error: %s] %s", cause.getClass().getSimpleName(), this.key.key());
        return new TranslatedMessage(Component.text(fallbackText), this.key);
    }

    private static void debug(@NotNull final String message, @NotNull final Map<String, ?> context) {
        if (debugLoggingEnabled) {
            LOGGER.fine(() -> TranslationLogger.message(message, context));
        }
    }

    /**
     * Immutable configuration bundle used to wire the service with its repository, formatter, and locale resolver.
     * All components are expected to be thread-safe and refreshed collectively during reload sequences to maintain
     * consistent placeholder and locale behaviour.
     */
    public record ServiceConfiguration(
        @NotNull TranslationRepository repository,
        @NotNull MessageFormatter formatter,
        @NotNull LocaleResolver localeResolver
    ) {
        /**
         * Validates that none of the supplied collaborators are {@code null}.
         */
        public ServiceConfiguration {
            Objects.requireNonNull(repository, "Repository cannot be null");
            Objects.requireNonNull(formatter, "Formatter cannot be null");
            Objects.requireNonNull(localeResolver, "Locale resolver cannot be null");
        }
    }
}
