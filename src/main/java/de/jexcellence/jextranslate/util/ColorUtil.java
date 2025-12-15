package de.jexcellence.jextranslate.util;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for converting legacy Minecraft color codes to MiniMessage format.
 *
 * @author JExcellence
 * @since 3.0.0
 */
public final class ColorUtil {

    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("[&§]([0-9a-fk-or])");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("[&§]#([0-9a-fA-F]{6})");

    private ColorUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts legacy color codes (&amp; and §) to MiniMessage format.
     *
     * @param text the text containing legacy color codes
     * @return the text with MiniMessage tags
     */
    @NotNull
    public static String convertLegacyToMiniMessage(@NotNull final String text) {
        return convertLegacyColorsToMiniMessage(text);
    }

    /**
     * Converts legacy color codes (&amp; and §) to MiniMessage format.
     * Alias for {@link #convertLegacyToMiniMessage(String)} for R18n compatibility.
     *
     * @param text the text containing legacy color codes
     * @return the text with MiniMessage tags
     */
    @NotNull
    public static String convertLegacyColorsToMiniMessage(@NotNull final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;

        // Convert hex colors first (&#RRGGBB or §#RRGGBB)
        Matcher hexMatcher = HEX_COLOR_PATTERN.matcher(result);
        StringBuilder hexBuilder = new StringBuilder();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            hexMatcher.appendReplacement(hexBuilder, "<#" + hex + ">");
        }
        hexMatcher.appendTail(hexBuilder);
        result = hexBuilder.toString();

        // Convert standard color codes
        Matcher colorMatcher = LEGACY_COLOR_PATTERN.matcher(result);
        StringBuilder colorBuilder = new StringBuilder();
        while (colorMatcher.find()) {
            String code = colorMatcher.group(1).toLowerCase();
            String replacement = convertCodeToMiniMessage(code);
            colorMatcher.appendReplacement(colorBuilder, Matcher.quoteReplacement(replacement));
        }
        colorMatcher.appendTail(colorBuilder);

        return colorBuilder.toString();
    }

    /**
     * Converts a single legacy color code to its MiniMessage equivalent.
     */
    @NotNull
    private static String convertCodeToMiniMessage(@NotNull final String code) {
        return switch (code) {
            case "0" -> "<black>";
            case "1" -> "<dark_blue>";
            case "2" -> "<dark_green>";
            case "3" -> "<dark_aqua>";
            case "4" -> "<dark_red>";
            case "5" -> "<dark_purple>";
            case "6" -> "<gold>";
            case "7" -> "<gray>";
            case "8" -> "<dark_gray>";
            case "9" -> "<blue>";
            case "a" -> "<green>";
            case "b" -> "<aqua>";
            case "c" -> "<red>";
            case "d" -> "<light_purple>";
            case "e" -> "<yellow>";
            case "f" -> "<white>";
            case "k" -> "<obfuscated>";
            case "l" -> "<bold>";
            case "m" -> "<strikethrough>";
            case "n" -> "<underlined>";
            case "o" -> "<italic>";
            case "r" -> "<reset>";
            default -> "";
        };
    }

    /**
     * Strips all color codes (both legacy and MiniMessage) from text.
     *
     * @param text the text to strip
     * @return plain text without formatting
     */
    @NotNull
    public static String stripColors(@NotNull final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove legacy codes
        String result = text.replaceAll("[&§][0-9a-fk-or]", "");
        result = result.replaceAll("[&§]#[0-9a-fA-F]{6}", "");

        // Remove MiniMessage tags
        result = result.replaceAll("<[^>]+>", "");

        return result;
    }

    /**
     * Checks if the text contains any color codes.
     *
     * @param text the text to check
     * @return true if color codes are present
     */
    public static boolean hasColorCodes(@NotNull final String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return LEGACY_COLOR_PATTERN.matcher(text).find() 
                || HEX_COLOR_PATTERN.matcher(text).find()
                || text.contains("<");
    }
}
