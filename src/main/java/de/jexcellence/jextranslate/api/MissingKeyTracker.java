package de.jexcellence.jextranslate.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tracks translation keys that are requested but missing from the active {@link TranslationRepository}. Tracker data is
 * surfaced through administrative tooling such as {@link de.jexcellence.jextranslate.command.TranslationCommand} to aid
 * repository maintenance.
 *
 * <p>Implementations should maintain thread-safe collections, enabling integration with asynchronous repository reloads
 * triggered by {@link TranslationService} or scheduler tasks.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public interface MissingKeyTracker {

    /**
     * Records a missing translation key for the supplied locale.
     *
     * @param key    the missing translation key
     * @param locale the locale in which the key was missing
     */
    void trackMissing(@NotNull TranslationKey key, @NotNull Locale locale);

    /**
     * Records a missing translation key along with optional contextual metadata.
     *
     * @param key     the missing translation key
     * @param locale  the locale in which the key was missing
     * @param context optional free-form context string (e.g., command name)
     */
    default void trackMissing(@NotNull TranslationKey key, @NotNull Locale locale, @Nullable String context) {
        trackMissing(key, locale);
    }

    /**
     * Retrieves the missing keys for the supplied locale.
     *
     * @param locale the locale to inspect
     * @return immutable set of missing translation keys
     */
    @NotNull
    Set<TranslationKey> getMissingKeys(@NotNull Locale locale);

    /**
     * Retrieves all missing keys grouped by locale.
     *
     * @return immutable map of locales to missing key sets
     */
    @NotNull
    Map<Locale, Set<TranslationKey>> getAllMissingKeys();

    /**
     * Counts the total number of missing key entries across all locales.
     *
     * @return the global missing key count
     */
    int getTotalMissingCount();

    /**
     * Counts the missing keys for the supplied locale.
     *
     * @param locale the locale to inspect
     * @return the number of missing keys for that locale
     */
    int getMissingCount(@NotNull Locale locale);

    /**
     * Provides the locales that currently have missing keys recorded.
     *
     * @return immutable set of locales with missing key entries
     */
    @NotNull
    Set<Locale> getLocalesWithMissingKeys();

    /**
     * Determines whether a specific key is tracked as missing for the supplied locale.
     *
     * @param key    the translation key to check
     * @param locale the locale to inspect
     * @return {@code true} when the key is known to be missing
     */
    boolean isMissing(@NotNull TranslationKey key, @NotNull Locale locale);

    /**
     * Marks a missing key as resolved for the supplied locale, typically after a repository update.
     *
     * @param key    the translation key to resolve
     * @param locale the locale to modify
     * @return {@code true} when the key was previously missing and removed
     */
    boolean markResolved(@NotNull TranslationKey key, @NotNull Locale locale);

    /**
     * Clears missing keys for the supplied locale.
     *
     * @param locale the locale whose entries should be removed
     * @return the number of keys removed
     */
    int clearMissing(@NotNull Locale locale);

    /**
     * Clears every missing key entry across all locales.
     *
     * @return the number of keys removed
     */
    int clearAllMissing();

    /**
     * Provides the timestamp when tracking began.
     *
     * @return the start time of tracking
     */
    @NotNull
    LocalDateTime getTrackingStartTime();

    /**
     * Indicates whether tracking is currently enabled.
     *
     * @return {@code true} when missing key tracking is active
     */
    boolean isEnabled();

    /**
     * Enables or disables missing key tracking.
     *
     * @param enabled {@code true} to enable tracking, {@code false} to disable
     */
    void setEnabled(boolean enabled);

    /**
     * Provides aggregate statistics describing missing key activity.
     *
     * @return tracker statistics snapshot
     */
    @NotNull
    Statistics getStatistics();

    /**
     * Snapshot of missing key analytics used by diagnostics commands.
     */
    interface Statistics {
        /**
         * @return the total number of missing key tracking events since startup
         */
        long getTotalTrackingEvents();

        /**
         * @return the total number of unique missing keys encountered
         */
        int getUniqueMissingCount();

        /**
         * @return the number of locales affected by missing keys
         */
        int getAffectedLocaleCount();

        /**
         * @return the most frequently missing key, or {@code null} when unavailable
         */
        @Nullable
        TranslationKey getMostFrequentMissing();

        /**
         * @return the locale with the highest number of missing keys, or {@code null} when unavailable
         */
        @Nullable
        Locale getLocaleWithMostMissing();

        /**
         * @return the timestamp when tracking began
         */
        @NotNull
        LocalDateTime getTrackingStartTime();

        /**
         * @return the timestamp of the last missing key event, or {@code null} when none have occurred
         */
        @Nullable
        LocalDateTime getLastTrackingTime();

        /**
         * @return {@code true} when the tracker is currently enabled
         */
        boolean isEnabled();
    }
}
