package de.jexcellence.jextranslate.validation;

import de.jexcellence.jextranslate.R18nManager;
import de.jexcellence.jextranslate.config.R18nConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced key validation system for translation completeness and consistency.
 *
 * <p>This class provides comprehensive validation of translation keys across
 * all supported locales. It can detect:</p>
 * <ul>
 *   <li>Missing keys across locales</li>
 *   <li>Placeholder inconsistencies between locales</li>
 *   <li>MiniMessage format errors</li>
 *   <li>Naming convention violations</li>
 * </ul>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public final class KeyValidator {

    private static final Logger LOGGER = Logger.getLogger(KeyValidator.class.getName());
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    // Pattern for detecting placeholders: {name} or %name%
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}|%([^%]+)%");
    
    // Pattern for valid key naming: lowercase letters, numbers, dots, underscores
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9._]*$");

    private final R18nConfiguration configuration;

    public KeyValidator(@NotNull R18nConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Validates all translation keys across all supported locales.
     *
     * @return a comprehensive validation report
     */
    @NotNull
    public ValidationReport validateAllKeys() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Starting comprehensive key validation...");

        ValidationReport.Builder reportBuilder = ValidationReport.builder()
                .timestamp(Instant.now());

        Map<String, Map<String, List<String>>> translations = getTranslations();
        
        if (translations.isEmpty()) {
            LOGGER.warning("No translations loaded - skipping validation");
            return reportBuilder
                    .statistics(ValidationStatistics.empty())
                    .build();
        }

        String defaultLocale = configuration.defaultLocale();
        Set<String> supportedLocales = configuration.supportedLocales();
        Set<String> allKeys = translations.keySet();

        // Validate missing keys
        validateMissingKeys(translations, defaultLocale, supportedLocales, reportBuilder);

        // Validate placeholder consistency
        validatePlaceholderConsistency(translations, defaultLocale, supportedLocales, reportBuilder);

        // Validate MiniMessage format
        validateMiniMessageFormat(translations, reportBuilder);

        // Validate naming conventions
        validateNamingConventions(allKeys, reportBuilder);

        // Calculate statistics
        ValidationStatistics statistics = calculateStatistics(
                translations, supportedLocales, startTime);
        reportBuilder.statistics(statistics);

        ValidationReport report = reportBuilder.build();

        logValidationResults(report);

        return report;
    }

    /**
     * Validates missing keys across all locales.
     */
    private void validateMissingKeys(
            @NotNull Map<String, Map<String, List<String>>> translations,
            @NotNull String defaultLocale,
            @NotNull Set<String> supportedLocales,
            @NotNull ValidationReport.Builder reportBuilder) {

        for (Map.Entry<String, Map<String, List<String>>> entry : translations.entrySet()) {
            String key = entry.getKey();
            Map<String, List<String>> localeMap = entry.getValue();

            for (String locale : supportedLocales) {
                List<String> messages = localeMap.get(locale);
                boolean isMissing = messages == null || messages.isEmpty();

                if (isMissing && !locale.equals(defaultLocale)) {
                    // Key is missing in this locale but exists in default
                    List<String> defaultMessages = localeMap.get(defaultLocale);
                    if (defaultMessages != null && !defaultMessages.isEmpty()) {
                        reportBuilder.addMissingKey(key + " [" + locale + "]");
                    }
                } else if (isMissing && locale.equals(defaultLocale)) {
                    // Key is missing in default locale - critical
                    reportBuilder.addMissingKey(key + " [DEFAULT:" + defaultLocale + "]");
                }
            }
        }
    }

    /**
     * Validates placeholder consistency between locales.
     */
    private void validatePlaceholderConsistency(
            @NotNull Map<String, Map<String, List<String>>> translations,
            @NotNull String defaultLocale,
            @NotNull Set<String> supportedLocales,
            @NotNull ValidationReport.Builder reportBuilder) {

        for (Map.Entry<String, Map<String, List<String>>> entry : translations.entrySet()) {
            String key = entry.getKey();
            Map<String, List<String>> localeMap = entry.getValue();

            // Get placeholders from default locale
            Set<String> defaultPlaceholders = extractPlaceholders(localeMap.get(defaultLocale));

            if (defaultPlaceholders.isEmpty()) {
                continue; // No placeholders to validate
            }

            // Compare with other locales
            for (String locale : supportedLocales) {
                if (locale.equals(defaultLocale)) continue;

                List<String> messages = localeMap.get(locale);
                if (messages == null || messages.isEmpty()) continue;

                Set<String> localePlaceholders = extractPlaceholders(messages);

                // Check for missing placeholders
                Set<String> missingInLocale = new HashSet<>(defaultPlaceholders);
                missingInLocale.removeAll(localePlaceholders);

                // Check for extra placeholders
                Set<String> extraInLocale = new HashSet<>(localePlaceholders);
                extraInLocale.removeAll(defaultPlaceholders);

                if (!missingInLocale.isEmpty() || !extraInLocale.isEmpty()) {
                    StringBuilder issue = new StringBuilder(key).append(" [").append(locale).append("]");
                    if (!missingInLocale.isEmpty()) {
                        issue.append(" missing: ").append(missingInLocale);
                    }
                    if (!extraInLocale.isEmpty()) {
                        issue.append(" extra: ").append(extraInLocale);
                    }
                    reportBuilder.addPlaceholderIssue(issue.toString());
                }
            }
        }
    }

    /**
     * Validates MiniMessage format in all translations.
     */
    private void validateMiniMessageFormat(
            @NotNull Map<String, Map<String, List<String>>> translations,
            @NotNull ValidationReport.Builder reportBuilder) {

        for (Map.Entry<String, Map<String, List<String>>> entry : translations.entrySet()) {
            String key = entry.getKey();
            Map<String, List<String>> localeMap = entry.getValue();

            for (Map.Entry<String, List<String>> localeEntry : localeMap.entrySet()) {
                String locale = localeEntry.getKey();
                List<String> messages = localeEntry.getValue();

                if (messages == null) continue;

                for (String message : messages) {
                    if (!isValidMiniMessage(message)) {
                        reportBuilder.addFormatError(key + " [" + locale + "]: Invalid MiniMessage format");
                    }
                }
            }
        }
    }

    /**
     * Validates key naming conventions.
     */
    private void validateNamingConventions(
            @NotNull Set<String> allKeys,
            @NotNull ValidationReport.Builder reportBuilder) {

        for (String key : allKeys) {
            if (!VALID_KEY_PATTERN.matcher(key).matches()) {
                // Check for common issues
                if (key.contains(" ")) {
                    reportBuilder.addNamingViolation(key + ": Contains spaces");
                } else if (key.matches(".*[A-Z].*")) {
                    reportBuilder.addNamingViolation(key + ": Contains uppercase letters");
                } else if (key.startsWith(".") || key.endsWith(".")) {
                    reportBuilder.addNamingViolation(key + ": Invalid dot placement");
                } else if (key.contains("..")) {
                    reportBuilder.addNamingViolation(key + ": Contains consecutive dots");
                }
            }
        }
    }

    /**
     * Extracts all placeholders from a list of messages.
     */
    @NotNull
    private Set<String> extractPlaceholders(List<String> messages) {
        Set<String> placeholders = new HashSet<>();
        if (messages == null) return placeholders;

        for (String message : messages) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
            while (matcher.find()) {
                String placeholder = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (placeholder != null) {
                    placeholders.add(placeholder);
                }
            }
        }
        return placeholders;
    }

    /**
     * Checks if a message is valid MiniMessage format.
     */
    private boolean isValidMiniMessage(@NotNull String message) {
        try {
            // Replace placeholders with dummy values before parsing
            String testMessage = message
                    .replaceAll("\\{[^}]+}", "test")
                    .replaceAll("%[^%]+%", "test");
            MINI_MESSAGE.deserialize(testMessage);
            return true;
        } catch (Exception e) {
            if (configuration.debugMode()) {
                LOGGER.log(Level.FINE, "MiniMessage parse error: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Calculates validation statistics.
     */
    @NotNull
    private ValidationStatistics calculateStatistics(
            @NotNull Map<String, Map<String, List<String>>> translations,
            @NotNull Set<String> supportedLocales,
            long startTime) {

        int totalKeys = translations.size();
        int totalLocales = supportedLocales.size();

        Map<String, Integer> keysPerLocale = new HashMap<>();
        int totalTranslations = 0;
        int expectedTranslations = totalKeys * totalLocales;

        for (String locale : supportedLocales) {
            int count = 0;
            for (Map<String, List<String>> localeMap : translations.values()) {
                List<String> messages = localeMap.get(locale);
                if (messages != null && !messages.isEmpty()) {
                    count++;
                }
            }
            keysPerLocale.put(locale, count);
            totalTranslations += count;
        }

        double completeness = expectedTranslations > 0
                ? (totalTranslations * 100.0 / expectedTranslations)
                : 100.0;

        long duration = System.currentTimeMillis() - startTime;

        return new ValidationStatistics(
                totalKeys,
                totalLocales,
                keysPerLocale,
                completeness,
                duration
        );
    }

    /**
     * Logs validation results.
     */
    private void logValidationResults(@NotNull ValidationReport report) {
        if (report.hasIssues()) {
            LOGGER.warning(String.format(
                    "Validation completed with %d issues: %d missing keys, %d format errors, " +
                            "%d placeholder issues, %d naming violations",
                    report.getTotalIssues(),
                    report.missingKeys().size(),
                    report.formatErrors().size(),
                    report.placeholderIssues().size(),
                    report.namingViolations().size()
            ));
            LOGGER.info("Validation Score: " + String.format("%.1f%%", report.getValidationScore()));
        } else {
            LOGGER.info("Validation completed successfully - no issues found!");
            LOGGER.info("Completeness: " + String.format("%.1f%%", 
                    report.statistics().completenessPercentage()));
        }
    }

    /**
     * Gets translations from R18nManager instance.
     */
    @NotNull
    private Map<String, Map<String, List<String>>> getTranslations() {
        try {
            R18nManager manager = R18nManager.getInstance();
            if (manager != null) {
                return manager.getTranslationLoader().getAllTranslations();
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            LOGGER.warning("Could not access R18nManager translations: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Validates a single key for a specific locale.
     *
     * @param key    the translation key
     * @param locale the locale to validate
     * @return true if the key is valid for the locale
     */
    public boolean isKeyValid(@NotNull String key, @NotNull String locale) {
        Map<String, Map<String, List<String>>> translations = getTranslations();
        Map<String, List<String>> localeMap = translations.get(key);
        
        if (localeMap == null) return false;
        
        List<String> messages = localeMap.get(locale);
        if (messages == null || messages.isEmpty()) {
            // Try default locale
            messages = localeMap.get(configuration.defaultLocale());
        }
        
        return messages != null && !messages.isEmpty();
    }

    /**
     * Gets missing keys for a specific locale.
     *
     * @param locale the locale to check
     * @return set of missing keys
     */
    @NotNull
    public Set<String> getMissingKeysForLocale(@NotNull String locale) {
        Set<String> missingKeys = new HashSet<>();
        Map<String, Map<String, List<String>>> translations = getTranslations();
        String defaultLocale = configuration.defaultLocale();

        for (Map.Entry<String, Map<String, List<String>>> entry : translations.entrySet()) {
            String key = entry.getKey();
            Map<String, List<String>> localeMap = entry.getValue();

            List<String> messages = localeMap.get(locale);
            List<String> defaultMessages = localeMap.get(defaultLocale);

            boolean missingInLocale = messages == null || messages.isEmpty();
            boolean existsInDefault = defaultMessages != null && !defaultMessages.isEmpty();

            if (missingInLocale && existsInDefault && !locale.equals(defaultLocale)) {
                missingKeys.add(key);
            }
        }

        return missingKeys;
    }

    @NotNull
    public R18nConfiguration getConfiguration() {
        return configuration;
    }
}
