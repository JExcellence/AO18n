package de.jexcellence.jextranslate.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface MissingKeyTracker {

    void trackMissing(@NotNull TranslationKey key, @NotNull Locale locale);

    default void trackMissing(@NotNull TranslationKey key, @NotNull Locale locale, @Nullable String context) {
        trackMissing(key, locale);
    }

    @NotNull
    Set<TranslationKey> getMissingKeys(@NotNull Locale locale);

    @NotNull
    Map<Locale, Set<TranslationKey>> getAllMissingKeys();

    int getTotalMissingCount();

    int getMissingCount(@NotNull Locale locale);

    @NotNull
    Set<Locale> getLocalesWithMissingKeys();

    boolean isMissing(@NotNull TranslationKey key, @NotNull Locale locale);

    boolean markResolved(@NotNull TranslationKey key, @NotNull Locale locale);

    int clearMissing(@NotNull Locale locale);

    int clearAllMissing();

    @NotNull
    LocalDateTime getTrackingStartTime();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    @NotNull
    Statistics getStatistics();

    interface Statistics {
        long getTotalTrackingEvents();

        int getUniqueMissingCount();

        int getAffectedLocaleCount();

        @Nullable
        TranslationKey getMostFrequentMissing();

        @Nullable
        Locale getLocaleWithMostMissing();

        @NotNull
        LocalDateTime getTrackingStartTime();

        @Nullable
        LocalDateTime getLastTrackingTime();

        boolean isEnabled();
    }
}
