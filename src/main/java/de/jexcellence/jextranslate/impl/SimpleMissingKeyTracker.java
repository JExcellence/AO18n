package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.MissingKeyTracker;
import de.jexcellence.jextranslate.api.TranslationKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleMissingKeyTracker implements MissingKeyTracker {

    private final Map<Locale, Set<TranslationKey>> missingKeys = new ConcurrentHashMap<>();
    private final Map<TranslationKey, AtomicLong> trackingCounts = new ConcurrentHashMap<>();
    private final LocalDateTime startTime = LocalDateTime.now();
    private volatile LocalDateTime lastTrackingTime;
    private volatile boolean enabled = true;

    @Override
    public void trackMissing(@NotNull final TranslationKey key, @NotNull final Locale locale) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        if (!this.enabled) {
            return;
        }

        this.missingKeys.computeIfAbsent(locale, k -> ConcurrentHashMap.newKeySet()).add(key);
        this.trackingCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        this.lastTrackingTime = LocalDateTime.now();
    }

    @Override
    @NotNull
    public Set<TranslationKey> getMissingKeys(@NotNull final Locale locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        return Set.copyOf(this.missingKeys.getOrDefault(locale, Set.of()));
    }

    @Override
    @NotNull
    public Map<Locale, Set<TranslationKey>> getAllMissingKeys() {
        final Map<Locale, Set<TranslationKey>> result = new HashMap<>();
        for (final Map.Entry<Locale, Set<TranslationKey>> entry : this.missingKeys.entrySet()) {
            result.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    @Override
    public int getTotalMissingCount() {
        return this.missingKeys.values().stream()
            .mapToInt(Set::size)
            .sum();
    }

    @Override
    public int getMissingCount(@NotNull final Locale locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        return this.missingKeys.getOrDefault(locale, Set.of()).size();
    }

    @Override
    @NotNull
    public Set<Locale> getLocalesWithMissingKeys() {
        return Set.copyOf(this.missingKeys.keySet());
    }

    @Override
    public boolean isMissing(@NotNull final TranslationKey key, @NotNull final Locale locale) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");
        return this.missingKeys.getOrDefault(locale, Set.of()).contains(key);
    }

    @Override
    public boolean markResolved(@NotNull final TranslationKey key, @NotNull final Locale locale) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        final Set<TranslationKey> keys = this.missingKeys.get(locale);
        if (keys != null) {
            return keys.remove(key);
        }
        return false;
    }

    @Override
    public int clearMissing(@NotNull final Locale locale) {
        Objects.requireNonNull(locale, "Locale cannot be null");
        final Set<TranslationKey> removed = this.missingKeys.remove(locale);
        return removed != null ? removed.size() : 0;
    }

    @Override
    public int clearAllMissing() {
        final int count = getTotalMissingCount();
        this.missingKeys.clear();
        this.trackingCounts.clear();
        return count;
    }

    @Override
    @NotNull
    public LocalDateTime getTrackingStartTime() {
        return this.startTime;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    @NotNull
    public Statistics getStatistics() {
        return new StatisticsImpl();
    }

    private final class StatisticsImpl implements Statistics {

        @Override
        public long getTotalTrackingEvents() {
            return trackingCounts.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
        }

        @Override
        public int getUniqueMissingCount() {
            return (int) missingKeys.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .count();
        }

        @Override
        public int getAffectedLocaleCount() {
            return missingKeys.size();
        }

        @Override
        @Nullable
        public TranslationKey getMostFrequentMissing() {
            return trackingCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue(Comparator.comparingLong(AtomicLong::get)))
                .map(Map.Entry::getKey)
                .orElse(null);
        }

        @Override
        @Nullable
        public Locale getLocaleWithMostMissing() {
            return missingKeys.entrySet().stream()
                .max(Map.Entry.comparingByValue(Comparator.comparingInt(Set::size)))
                .map(Map.Entry::getKey)
                .orElse(null);
        }

        @Override
        @NotNull
        public LocalDateTime getTrackingStartTime() {
            return startTime;
        }

        @Override
        @Nullable
        public LocalDateTime getLastTrackingTime() {
            return lastTrackingTime;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
