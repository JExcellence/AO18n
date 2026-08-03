package de.jexcellence.jextranslate.validation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Statistics record for validation results.
 *
 * @param totalKeys              total number of translation keys
 * @param totalLocales           total number of locales
 * @param keysPerLocale          map of locale to key count
 * @param completenessPercentage overall completeness percentage
 * @param validationDurationMs   validation duration in milliseconds
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public record ValidationStatistics(
        int totalKeys,
        int totalLocales,
        @NotNull Map<String, Integer> keysPerLocale,
        double completenessPercentage,
        long validationDurationMs
) {

    /**
     * Creates an empty statistics record.
     *
     * @return empty statistics
     */
    @NotNull
    public static ValidationStatistics empty() {
        return new ValidationStatistics(0, 0, Map.of(), 100.0, 0);
    }
}
