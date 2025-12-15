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

    public boolean hasIssues() {
        return !missingKeys.isEmpty() || !unusedKeys.isEmpty() || !formatErrors.isEmpty() ||
                !placeholderIssues.isEmpty() || !namingViolations.isEmpty();
    }

    public int getTotalIssues() {
        return missingKeys.size() + unusedKeys.size() + formatErrors.size() +
                placeholderIssues.size() + namingViolations.size();
    }

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

    public double getValidationScore() {
        int totalKeys = statistics.totalKeys();
        if (totalKeys == 0) return 100.0;
        int issues = getTotalIssues();
        return Math.max(0.0, 100.0 - (issues * 100.0 / totalKeys));
    }

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

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Instant timestamp = Instant.now();
        private final Set<String> missingKeys = new HashSet<>();
        private final Set<String> unusedKeys = new HashSet<>();
        private final Set<String> formatErrors = new HashSet<>();
        private final Set<String> placeholderIssues = new HashSet<>();
        private final Set<String> namingViolations = new HashSet<>();
        private ValidationStatistics statistics = ValidationStatistics.empty();

        @NotNull
        public Builder timestamp(@NotNull Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @NotNull
        public Builder addMissingKey(@NotNull String key) {
            this.missingKeys.add(key);
            return this;
        }

        @NotNull
        public Builder addMissingKeys(@NotNull Collection<String> keys) {
            this.missingKeys.addAll(keys);
            return this;
        }

        @NotNull
        public Builder addFormatError(@NotNull String key) {
            this.formatErrors.add(key);
            return this;
        }

        @NotNull
        public Builder addPlaceholderIssue(@NotNull String key) {
            this.placeholderIssues.add(key);
            return this;
        }

        @NotNull
        public Builder addNamingViolation(@NotNull String key) {
            this.namingViolations.add(key);
            return this;
        }

        @NotNull
        public Builder statistics(@NotNull ValidationStatistics statistics) {
            this.statistics = statistics;
            return this;
        }

        @NotNull
        public ValidationReport build() {
            return new ValidationReport(timestamp, missingKeys, unusedKeys, formatErrors,
                    placeholderIssues, namingViolations, statistics);
        }
    }
}
