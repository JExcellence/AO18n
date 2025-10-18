package de.jexcellence.jextranslate.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves player locales for use by {@link TranslationService}. Implementations may integrate with client locale
 * detection APIs, persistent storage, or manual overrides. Locale resolution feeds directly into repository lookups and
 * placeholder formatting.
 *
 * <p>Resolvers should cache or persist per-player locale preferences and must remain synchronized with the
 * {@link TranslationRepository} and {@link MessageFormatter} supplied during
 * {@link TranslationService#configure(TranslationService.ServiceConfiguration)}.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public interface LocaleResolver {

    /**
     * Resolves the locale for the supplied player.
     *
     * @param player the player requiring locale resolution
     * @return an {@link Optional} containing the resolved locale when available
     */
    @NotNull
    Optional<Locale> resolveLocale(@NotNull Player player);

    /**
     * Gets the resolver's default locale used when player-specific locales are unavailable.
     *
     * @return the default locale
     */
    @NotNull
    Locale getDefaultLocale();

    /**
     * Updates the resolver's default locale. Implementations should also ensure the associated
     * {@link TranslationRepository} is updated when necessary to avoid cascading mismatches.
     *
     * @param locale the locale to become the new default
     */
    void setDefaultLocale(@NotNull Locale locale);

    /**
     * Persists a locale preference for the supplied player. Implementations should return {@code true} when the locale
     * can be stored for subsequent calls.
     *
     * @param player the player whose preference should be saved
     * @param locale the locale to persist
     * @return {@code true} when the locale could be stored
     */
    default boolean setPlayerLocale(@NotNull Player player, @NotNull Locale locale) {
        return false;
    }

    /**
     * Clears any stored locale preference for the supplied player.
     *
     * @param player the player whose stored locale should be cleared
     * @return {@code true} when a locale was previously stored and removed
     */
    default boolean clearPlayerLocale(@NotNull Player player) {
        return false;
    }

    /**
     * Indicates whether the resolver supports storing player-specific locales.
     *
     * @return {@code true} when persistent storage is supported
     */
    default boolean supportsLocaleStorage() {
        return false;
    }
}
