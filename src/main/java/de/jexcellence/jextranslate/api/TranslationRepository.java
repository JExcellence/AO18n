package de.jexcellence.jextranslate.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction representing a translation data source. Implementations, such as
 * {@link de.jexcellence.jextranslate.impl.YamlTranslationRepository}, provide locale-aware string lookups for use by
 * {@link TranslationService}. Repositories coordinate closely with {@link MessageFormatter} to ensure placeholder
 * consistency when templates change.
 *
 * <p>Repositories should notify listeners when translations reload so dependent caches, including the
 * {@link TranslationService} locale cache and MiniMessage formatter instances, can be refreshed.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public interface TranslationRepository {

    /**
     * Retrieves the translation associated with the supplied key and locale. Implementations should implement locale
     * fallbacks (e.g., language-only) consistent with module conventions.
     *
     * @param key    the translation key to resolve
     * @param locale the locale context for the lookup
     * @return an {@link Optional} containing the translation when available
     */
    @NotNull
    Optional<String> getTranslation(@NotNull TranslationKey key, @NotNull Locale locale);

    /**
     * Asynchronously retrieves the translation associated with the supplied key and locale. The default implementation
     * delegates to {@link #getTranslation(TranslationKey, Locale)} using a background thread pool.
     *
     * @param key    the translation key to resolve
     * @param locale the locale context for the lookup
     * @return a future that completes with the translation result
     */
    @NotNull
    default CompletableFuture<Optional<String>> getTranslationAsync(@NotNull TranslationKey key, @NotNull Locale locale) {
        return CompletableFuture.supplyAsync(() -> getTranslation(key, locale));
    }

    /**
     * Indicates whether a translation exists for the supplied key and locale.
     *
     * @param key    the translation key to check
     * @param locale the locale context for the lookup
     * @return {@code true} if a translation is present
     */
    default boolean hasTranslation(@NotNull TranslationKey key, @NotNull Locale locale) {
        return getTranslation(key, locale).isPresent();
    }

    /**
     * Provides the locales available within the repository.
     *
     * @return immutable set of supported locales
     */
    @NotNull
    Set<Locale> getAvailableLocales();

    /**
     * Gets the repository-wide default locale used when resolvers cannot provide a player-specific locale.
     *
     * @return the default locale
     */
    @NotNull
    Locale getDefaultLocale();

    /**
     * Updates the repository default locale. Implementations should ensure the locale is part of the supported set to
     * keep {@link TranslationService} locale cascades predictable.
     *
     * @param locale the locale to use as the repository default
     */
    void setDefaultLocale(@NotNull Locale locale);

    /**
     * Retrieves the translation keys available for the supplied locale.
     *
     * @param locale the locale whose keys should be enumerated
     * @return immutable set of translation keys available for the locale
     */
    @NotNull
    Set<TranslationKey> getAvailableKeys(@NotNull Locale locale);

    /**
     * Retrieves all translation keys known to the repository across every locale.
     *
     * @return immutable set of all translation keys stored in the repository
     */
    @NotNull
    Set<TranslationKey> getAllAvailableKeys();

    /**
     * Reloads repository state, refreshing caches and notifying listeners. Implementations should execute I/O off the
     * primary server thread and complete once new data becomes available.
     *
     * @return a future that completes when the reload finishes
     */
    @NotNull
    CompletableFuture<Void> reload();

    /**
     * Registers a repository listener that will be notified on reloads, translation loads, and errors.
     *
     * @param listener the listener to register
     */
    void addListener(@NotNull RepositoryListener listener);

    /**
     * Removes a previously registered repository listener.
     *
     * @param listener the listener to unregister
     */
    void removeListener(@NotNull RepositoryListener listener);

    /**
     * Retrieves metadata about the repository, including source path and totals used by administrative commands.
     *
     * @return a metadata descriptor
     */
    @NotNull
    RepositoryMetadata getMetadata();

    /**
     * Listener contract used to react to repository lifecycle events.
     */
    interface RepositoryListener {
        /**
         * Called after a successful reload operation.
         *
         * @param repository the repository invoking the callback
         */
        default void onReload(@NotNull TranslationRepository repository) {}

        /**
         * Called each time a translation key/value pair is loaded from storage.
         *
         * @param repository the repository invoking the callback
         * @param key        the key that was loaded
         * @param locale     the locale of the translation
         * @param translation the resolved translation value
         */
        default void onTranslationLoaded(@NotNull TranslationRepository repository, @NotNull TranslationKey key, @NotNull Locale locale, @NotNull String translation) {}

        /**
         * Called when the repository encounters an unrecoverable error during reload or lookup.
         *
         * @param repository the repository invoking the callback
         * @param error      the exception that occurred
         */
        default void onError(@NotNull TranslationRepository repository, @NotNull Throwable error) {}
    }

    /**
     * Metadata describing repository state for diagnostics and command output.
     */
    interface RepositoryMetadata {
        /**
         * Provides the repository implementation type (e.g., {@code yaml}).
         *
         * @return human-readable type identifier
         */
        @NotNull
        String getType();

        /**
         * Provides the repository source, such as a directory or database connection string.
         *
         * @return the data source location
         */
        @NotNull
        String getSource();

        /**
         * Provides the last modified epoch timestamp, typically updated on reload.
         *
         * @return the last modification time in milliseconds since epoch
         */
        long getLastModified();

        /**
         * Provides the total number of translations currently loaded.
         *
         * @return the translation count
         */
        int getTotalTranslations();

        /**
         * Retrieves additional metadata properties. Keys are implementation-defined.
         *
         * @param key the property identifier
         * @return the property value or {@code null} when unavailable
         */
        @Nullable
        String getProperty(@NotNull String key);
    }
}
