package de.jexcellence.jextranslate.validation;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

/**
 * Comprehensive validation report for translation key analysis.
 *
 * @param timestamp         when the validation was performed
 * @param missingKeys       keys that are missing in one or more locales
 * @param unusedKeys        keys that exist but are never referenced
 * @param formatErrors      keys with formatting issues
 * @param placeholderIssues keys with placeholder inconsistencies
 * @param namingViolations  keys that don't follow naming conventions
 * @param statistics        validation statistics
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public record ValidationReport(
        @NotNull Instant timestamp,
        @NotNull Set<String> missingKeys,
        @NotNull Set<String> unusedKeys,
        @NotNull Set<String> formatErrors,
        @NotNull Set<String> placeholderIssues,
        @NotNull Set<String> namingViolations,
        @NotNull ValidationStatistics statistics
) {

    public ValidationReport {
        missingKeys = Collections.unmodifiableSet(new HashSet<>(missingKeys));
        unusedKeys = Collections.unmodifiableSet(new HashSet<>(unusedKeys));
        formatErrors = Collections.unmodifiableSet(new HashSet<>(formatErrors));
        placeholderIssues = Collections.unmodifiableSet(new HashSet<>(placeholderIssues));
        namingViolations = Collections.unmodifiableSet(new HashSet<>(namingViolations));
    }

    /**
     * Indicates whether the report contains any issues.
     *
     * @return {@code true} when at least one issue category is non-empty
     */
    public boolean hasIssues() {
        return !missingKeys.isEmpty() || !unusedKeys.isEmpty() || !formatErrors.isEmpty() ||
                !placeholderIssues.isEmpty() || !namingViolations.isEmpty();
    }

    /**
     * Returns the aggregate issue count across all tracked categories.
     *
     * @return total number of issues
     */
    public int getTotalIssues() {
        return missingKeys.size() + unusedKeys.size() + formatErrors.size() +
                placeholderIssues.size() + namingViolations.size();
    }

    /**
     * Returns a merged set of all keys involved in any issue category.
     *
     * @return set containing every problematic key
     */
    @NotNull
    public Set<String> getAllProblematicKeys() {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(missingKeys);
        allKeys.addAll(unusedKeys);
        allKeys.addAll(formatErrors);
        allKeys.addAll(placeholderIssues);
        allKeys.addAll(namingViolations);
        return allKeys;
    }

    /**
     * Calculates a validation score from the issue count and total key count.
     *
     * @return percentage score where {@code 100.0} indicates no issues
     */
    public double getValidationScore() {
        int totalKeys = statistics.totalKeys();
        if (totalKeys == 0) return 100.0;
        int issues = getTotalIssues();
        return Math.max(0.0, 100.0 - (issues * 100.0 / totalKeys));
    }

    /**
     * Builds a human-readable report summary.
     *
     * @return multi-line summary string
     */
    @NotNull
    public String getSummary() {
        if (!hasIssues()) {
            return "✓ All translations are valid and complete!";
        }

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Validation Score: %.1f%%\n", getValidationScore()));
        summary.append(String.format("Total Issues: %d\n", getTotalIssues()));

        if (!missingKeys.isEmpty()) summary.append(String.format("- Missing Keys: %d\n", missingKeys.size()));
        if (!unusedKeys.isEmpty()) summary.append(String.format("- Unused Keys: %d\n", unusedKeys.size()));
        if (!formatErrors.isEmpty()) summary.append(String.format("- Format Errors: %d\n", formatErrors.size()));
        if (!placeholderIssues.isEmpty()) summary.append(String.format("- Placeholder Issues: %d\n", placeholderIssues.size()));
        if (!namingViolations.isEmpty()) summary.append(String.format("- Naming Violations: %d\n", namingViolations.size()));

        return summary.toString();
    }

    /**
     * Creates a builder for constructing immutable validation reports.
     *
     * @return empty builder instance
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder used to construct {@link ValidationReport} instances.
     */
    public static final class Builder {
        private Instant timestamp = Instant.now();
        private final Set<String> missingKeys = new HashSet<>();
        private final Set<String> unusedKeys = new HashSet<>();
        private final Set<String> formatErrors = new HashSet<>();
        private final Set<String> placeholderIssues = new HashSet<>();
        private final Set<String> namingViolations = new HashSet<>();
        private ValidationStatistics statistics = ValidationStatistics.empty();

        /**
         * Sets the report timestamp.
         *
         * @param timestamp validation time to store in the report
         * @return this builder
         */
        @NotNull
        public Builder timestamp(@NotNull Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Adds a key that is missing from at least one locale.
         *
         * @param key missing translation key
         * @return this builder
         */
        @NotNull
        public Builder addMissingKey(@NotNull String key) {
            this.missingKeys.add(key);
            return this;
        }

        /**
         * Adds multiple keys that are missing from at least one locale.
         *
         * @param keys missing translation keys
         * @return this builder
         */
        @NotNull
        public Builder addMissingKeys(@NotNull Collection<String> keys) {
            this.missingKeys.addAll(keys);
            return this;
        }

        /**
         * Adds a key with one or more formatting errors.
         *
         * @param key translation key with invalid formatting
         * @return this builder
         */
        @NotNull
        public Builder addFormatError(@NotNull String key) {
            this.formatErrors.add(key);
            return this;
        }

        /**
         * Adds a key with inconsistent or invalid placeholders.
         *
         * @param key translation key with placeholder issues
         * @return this builder
         */
        @NotNull
        public Builder addPlaceholderIssue(@NotNull String key) {
            this.placeholderIssues.add(key);
            return this;
        }

        /**
         * Adds a key that violates naming conventions.
         *
         * @param key translation key with naming violations
         * @return this builder
         */
        @NotNull
        public Builder addNamingViolation(@NotNull String key) {
            this.namingViolations.add(key);
            return this;
        }

        /**
         * Sets aggregate validation statistics.
         *
         * @param statistics computed validation statistics
         * @return this builder
         */
        @NotNull
        public Builder statistics(@NotNull ValidationStatistics statistics) {
            this.statistics = statistics;
            return this;
        }

        /**
         * Builds an immutable report from the collected values.
         *
         * @return new validation report snapshot
         */
        @NotNull
        public ValidationReport build() {
            return new ValidationReport(timestamp, missingKeys, unusedKeys, formatErrors,
                    placeholderIssues, namingViolations, statistics);
        }
    }
}
