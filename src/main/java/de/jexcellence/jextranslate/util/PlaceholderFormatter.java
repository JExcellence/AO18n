package de.jexcellence.jextranslate.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced placeholder formatting with ICU-style plurals, choice formats,
 * and locale-aware number/date formatting.
 *
 * @author JExcellence
 * @since 3.0.0
 */
public final class PlaceholderFormatter {

    // Pattern for {key, type, style} format
    private static final Pattern FORMAT_PATTERN = Pattern.compile(
            "\\{(\\w+)(?:,\\s*(number|date|time|choice|plural)(?:,\\s*([^}]+))?)?}"
    );

    // Pattern for simple {key} or %key% placeholders
    private static final Pattern SIMPLE_PATTERN = Pattern.compile("\\{(\\w+)}|%(\\w+)%");

    private PlaceholderFormatter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Formats a template with advanced placeholder support.
     *
     * @param template     the template string
     * @param placeholders map of placeholder values
     * @param locale       the locale for formatting
     * @return the formatted string
     */
    @NotNull
    public static String format(@NotNull String template, @NotNull Map<String, Object> placeholders, @NotNull Locale locale) {
        Objects.requireNonNull(template, "Template cannot be null");
        Objects.requireNonNull(placeholders, "Placeholders cannot be null");
        Objects.requireNonNull(locale, "Locale cannot be null");

        var result = template;

        // Process advanced format patterns first
        result = processAdvancedFormats(result, placeholders, locale);

        // Then process simple placeholders
        result = processSimplePlaceholders(result, placeholders);

        return result;
    }

    private static String processAdvancedFormats(String template, Map<String, Object> placeholders, Locale locale) {
        var matcher = FORMAT_PATTERN.matcher(template);
        var result = new StringBuilder();

        while (matcher.find()) {
            var key = matcher.group(1);
            var type = matcher.group(2);
            var style = matcher.group(3);
            var value = placeholders.get(key);

            String replacement;
            if (value == null) {
                replacement = matcher.group(0); // Keep original if no value
            } else if (type == null) {
                replacement = value.toString();
            } else {
                replacement = switch (type.toLowerCase()) {
                    case "number" -> formatNumber(value, style, locale);
                    case "date" -> formatDate(value, style, locale);
                    case "time" -> formatTime(value, style, locale);
                    case "choice" -> formatChoice(value, style);
                    case "plural" -> formatPlural(value, style, locale);
                    default -> value.toString();
                };
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String processSimplePlaceholders(String template, Map<String, Object> placeholders) {
        var matcher = SIMPLE_PATTERN.matcher(template);
        var result = new StringBuilder();

        while (matcher.find()) {
            var key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            var value = placeholders.get(key);
            var replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    @NotNull
    private static String formatNumber(@Nullable Object value, @Nullable String style, @NotNull Locale locale) {
        if (value == null) return "";

        var number = toNumber(value);
        if (number == null) return value.toString();

        var formatter = switch (style != null ? style.toLowerCase() : "") {
            case "integer" -> NumberFormat.getIntegerInstance(locale);
            case "currency" -> NumberFormat.getCurrencyInstance(locale);
            case "percent" -> NumberFormat.getPercentInstance(locale);
            default -> NumberFormat.getNumberInstance(locale);
        };

        return formatter.format(number);
    }

    @NotNull
    private static String formatDate(@Nullable Object value, @Nullable String style, @NotNull Locale locale) {
        if (value == null) return "";

        var formatStyle = parseFormatStyle(style);
        var formatter = DateTimeFormatter.ofLocalizedDate(formatStyle).withLocale(locale);

        return switch (value) {
            case LocalDate ld -> ld.format(formatter);
            case LocalDateTime ldt -> ldt.toLocalDate().format(formatter);
            case ZonedDateTime zdt -> zdt.toLocalDate().format(formatter);
            case Date d -> d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(formatter);
            default -> value.toString();
        };
    }

    @NotNull
    private static String formatTime(@Nullable Object value, @Nullable String style, @NotNull Locale locale) {
        if (value == null) return "";

        var formatStyle = parseFormatStyle(style);
        var formatter = DateTimeFormatter.ofLocalizedTime(formatStyle).withLocale(locale);

        return switch (value) {
            case LocalDateTime ldt -> ldt.toLocalTime().format(formatter);
            case ZonedDateTime zdt -> zdt.toLocalTime().format(formatter);
            case Date d -> d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(formatter);
            default -> value.toString();
        };
    }

    @NotNull
    private static String formatChoice(@Nullable Object value, @Nullable String pattern) {
        if (value == null || pattern == null) return "";

        var number = toNumber(value);
        if (number == null) return value.toString();

        try {
            var choiceFormat = new ChoiceFormat(pattern);
            return choiceFormat.format(number.doubleValue());
        } catch (Exception e) {
            return value.toString();
        }
    }

    @NotNull
    private static String formatPlural(@Nullable Object value, @Nullable String pattern, @NotNull Locale locale) {
        if (value == null || pattern == null) return "";

        var number = toNumber(value);
        if (number == null) return value.toString();

        // Simple plural pattern: "one {# item} other {# items}"
        var count = number.longValue();
        var parts = pattern.split("\\s+(?=one|other|zero|two|few|many)");

        String result = null;
        String otherResult = null;

        for (var part : parts) {
            part = part.trim();
            if (part.startsWith("one ") && count == 1) {
                result = extractPluralValue(part.substring(4), count);
            } else if (part.startsWith("zero ") && count == 0) {
                result = extractPluralValue(part.substring(5), count);
            } else if (part.startsWith("two ") && count == 2) {
                result = extractPluralValue(part.substring(4), count);
            } else if (part.startsWith("other ")) {
                otherResult = extractPluralValue(part.substring(6), count);
            }
        }

        return result != null ? result : (otherResult != null ? otherResult : value.toString());
    }

    private static String extractPluralValue(String pattern, long count) {
        // Remove surrounding braces if present
        var trimmed = pattern.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        // Replace # with the actual count
        return trimmed.replace("#", String.valueOf(count));
    }

    @Nullable
    private static Number toNumber(@Nullable Object value) {
        return switch (value) {
            case Number n -> n;
            case String s -> {
                try {
                    yield Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case null, default -> null;
        };
    }

    private static FormatStyle parseFormatStyle(@Nullable String style) {
        if (style == null) return FormatStyle.MEDIUM;
        return switch (style.toLowerCase()) {
            case "short" -> FormatStyle.SHORT;
            case "long" -> FormatStyle.LONG;
            case "full" -> FormatStyle.FULL;
            default -> FormatStyle.MEDIUM;
        };
    }
}
