package de.jexcellence.jextranslate.core;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Tracks translation usage metrics for monitoring and analysis.
 *
 * <p>This class provides thread-safe tracking of translation key usage,
 * missing key occurrences, and locale distribution. All counters use
 * {@link AtomicLong} for thread-safe increments.</p>
 *
 * <p><strong>Tracked Metrics:</strong></p>
 * <ul>
 *   <li>Key usage counts - how often each translation key is accessed</li>
 *   <li>Missing key occurrences - how often missing keys are encountered</li>
 *   <li>Locale distribution - how often each locale is used</li>
 * </ul>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class TranslationMetrics {

    private final Map<String, AtomicLong> keyUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> missingKeys = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> localeUsage = new ConcurrentHashMap<>();

    /**
     * Records usage of a translation key for a specific locale.
     *
     * @param key    the translation key that was accessed
     * @param locale the locale that was used
     */
    public void recordKeyUsage(@NotNull String key, @NotNull String locale) {
        keyUsage.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        localeUsage.computeIfAbsent(locale, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Records a missing translation key occurrence.
     *
     * @param key    the missing translation key
     * @param locale the locale for which the key was missing
     */
    public void recordMissingKey(@NotNull String key, @NotNull String locale) {
        String compositeKey = key + ":" + locale;
        missingKeys.computeIfAbsent(compositeKey, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Gets the most frequently used translation keys.
     *
     * @param limit the maximum number of keys to return
     * @return a list of entries sorted by usage count (descending)
     */
    @NotNull
    public List<Map.Entry<String, Long>> getMostUsedKeys(int limit) {
        return keyUsage.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(limit)
                .map(e -> Map.entry(e.getKey(), e.getValue().get()))
                .collect(Collectors.toList());
    }

    /**
     * Gets all missing key occurrences with their counts.
     *
     * <p>The returned map uses composite keys in the format "key:locale".</p>
     *
     * @return a map of missing key occurrences to their counts
     */
    @NotNull
    public Map<String, Long> getMissingKeyOccurrences() {
        return missingKeys.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get()
                ));
    }

    /**
     * Gets the locale usage distribution.
     *
     * @return a map of locale codes to their usage counts
     */
    @NotNull
    public Map<String, Long> getLocaleDistribution() {
        return localeUsage.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get()
                ));
    }

    /**
     * Gets the total number of translation requests.
     *
     * @return the total count of all key usages
     */
    public long getTotalRequests() {
        return keyUsage.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    /**
     * Gets the total number of missing key occurrences.
     *
     * @return the total count of all missing key occurrences
     */
    public long getTotalMissingKeyOccurrences() {
        return missingKeys.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    /**
     * Gets the number of unique keys that have been accessed.
     *
     * @return the count of unique keys
     */
    public int getUniqueKeyCount() {
        return keyUsage.size();
    }

    /**
     * Gets the number of unique missing keys encountered.
     *
     * @return the count of unique missing keys
     */
    public int getUniqueMissingKeyCount() {
        return missingKeys.size();
    }

    /**
     * Resets all metrics to zero.
     */
    public void reset() {
        keyUsage.clear();
        missingKeys.clear();
        localeUsage.clear();
    }
}
