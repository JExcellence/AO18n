package de.jexcellence.jextranslate.util;

import org.jetbrains.annotations.NotNull;

/**
 * Utility class for selecting plural forms based on ICU plural rules.
 *
 * <p>This class implements simplified ICU plural rules for various languages,
 * supporting the standard plural categories: zero, one, two, few, many, other.</p>
 *
 * <p>Supported language families:</p>
 * <ul>
 *   <li>Germanic (English, German)</li>
 *   <li>Romance (French, Spanish, Italian, Portuguese)</li>
 *   <li>Slavic (Russian, Ukrainian, Polish)</li>
 *   <li>Semitic (Arabic)</li>
 *   <li>CJK (Chinese, Japanese, Korean) - no plural forms</li>
 * </ul>
 *
 * @author JExcellence
 * @version 4.0.0
 * @since 4.0.0
 */
public final class PluralRules {

    /**
     * Plural form for zero items (used in some languages like Arabic).
     */
    public static final String ZERO = "zero";

    /**
     * Plural form for exactly one item.
     */
    public static final String ONE = "one";

    /**
     * Plural form for exactly two items (used in Arabic, Welsh, etc.).
     */
    public static final String TWO = "two";

    /**
     * Plural form for few items (used in Slavic languages).
     */
    public static final String FEW = "few";

    /**
     * Plural form for many items (used in Slavic languages, Arabic).
     */
    public static final String MANY = "many";

    /**
     * Default plural form when no other form applies.
     */
    public static final String OTHER = "other";

    private PluralRules() {
        // Utility class - prevent instantiation
    }

    /**
     * Selects the appropriate plural form for the given locale and count.
     *
     * @param locale the locale code (e.g., "en_US", "de_DE", "ru")
     * @param count  the count to determine plural form for
     * @return the plural form category (one, other, few, many, etc.)
     */
    @NotNull
    public static String select(@NotNull String locale, int count) {
        String language = extractLanguage(locale);

        return switch (language) {
            // Germanic languages (simple singular/plural)
            case "en", "de" -> selectGermanic(count);

            // Romance languages (French treats 0 and 1 as singular)
            case "fr" -> selectFrench(count);
            case "es", "it", "pt" -> selectRomance(count);

            // Slavic languages (complex rules)
            case "ru", "uk" -> selectSlavic(count);
            case "pl" -> selectPolish(count);

            // Arabic (most complex)
            case "ar" -> selectArabic(count);

            // CJK languages (no plural forms)
            case "ja", "ko", "zh" -> OTHER;

            // Default to simple singular/plural
            default -> count == 1 ? ONE : OTHER;
        };
    }

    /**
     * Extracts the language code from a locale string.
     *
     * @param locale the locale string (e.g., "en_US", "de-DE", "fr")
     * @return the language code (e.g., "en", "de", "fr")
     */
    @NotNull
    private static String extractLanguage(@NotNull String locale) {
        return locale.split("[_-]")[0].toLowerCase();
    }

    /**
     * Germanic plural rules (English, German).
     * Simple: 1 = one, everything else = other
     */
    @NotNull
    private static String selectGermanic(int count) {
        return count == 1 ? ONE : OTHER;
    }

    /**
     * French plural rules.
     * 0 and 1 are singular, everything else is plural.
     */
    @NotNull
    private static String selectFrench(int count) {
        return count <= 1 ? ONE : OTHER;
    }

    /**
     * Romance plural rules (Spanish, Italian, Portuguese).
     * Simple: 1 = one, everything else = other
     */
    @NotNull
    private static String selectRomance(int count) {
        return count == 1 ? ONE : OTHER;
    }

    /**
     * Slavic plural rules (Russian, Ukrainian).
     * Complex rules based on last digits.
     *
     * <ul>
     *   <li>one: ends in 1, but not 11</li>
     *   <li>few: ends in 2-4, but not 12-14</li>
     *   <li>many: ends in 0, 5-9, or 11-14</li>
     * </ul>
     */
    @NotNull
    private static String selectSlavic(int count) {
        int absCount = Math.abs(count);
        int mod10 = absCount % 10;
        int mod100 = absCount % 100;

        if (mod10 == 1 && mod100 != 11) {
            return ONE;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return FEW;
        }
        return MANY;
    }

    /**
     * Polish plural rules.
     * Similar to Slavic but with slight differences.
     *
     * <ul>
     *   <li>one: exactly 1</li>
     *   <li>few: ends in 2-4, but not 12-14</li>
     *   <li>many: everything else</li>
     * </ul>
     */
    @NotNull
    private static String selectPolish(int count) {
        int absCount = Math.abs(count);
        int mod10 = absCount % 10;
        int mod100 = absCount % 100;

        if (absCount == 1) {
            return ONE;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return FEW;
        }
        return MANY;
    }

    /**
     * Arabic plural rules.
     * Most complex with six forms.
     *
     * <ul>
     *   <li>zero: 0</li>
     *   <li>one: 1</li>
     *   <li>two: 2</li>
     *   <li>few: 3-10, or ends in 03-10</li>
     *   <li>many: 11-99, or ends in 11-99</li>
     *   <li>other: 100+, or ends in 00-02</li>
     * </ul>
     */
    @NotNull
    private static String selectArabic(int count) {
        int absCount = Math.abs(count);
        int mod100 = absCount % 100;

        if (absCount == 0) {
            return ZERO;
        }
        if (absCount == 1) {
            return ONE;
        }
        if (absCount == 2) {
            return TWO;
        }
        if (mod100 >= 3 && mod100 <= 10) {
            return FEW;
        }
        if (mod100 >= 11 && mod100 <= 99) {
            return MANY;
        }
        return OTHER;
    }
}
