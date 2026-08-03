package de.jexcellence.jextranslate.bedrock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for converting Adventure Components and MiniMessage strings to Bedrock-compatible legacy format.
 *
 * <p>Bedrock Edition clients only support legacy color codes (§ codes) and plain strings. This converter
 * handles the transformation of rich text components to a format that displays correctly on Bedrock clients.
 *
 * @since 3.1.0
 */
public final class BedrockConverter {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Pattern HEX_PATTERN = Pattern.compile("<#([0-9a-fA-F]{6})>");
    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient(?::[^>]*)?>([^<]*)</gradient>");

    /**
     * RGB values for all 16 legacy Minecraft colors.
     */
    private static final Map<NamedTextColor, int[]> LEGACY_COLOR_RGB = Map.ofEntries(
            Map.entry(NamedTextColor.BLACK, new int[]{0, 0, 0}),
            Map.entry(NamedTextColor.DARK_BLUE, new int[]{0, 0, 170}),
            Map.entry(NamedTextColor.DARK_GREEN, new int[]{0, 170, 0}),
            Map.entry(NamedTextColor.DARK_AQUA, new int[]{0, 170, 170}),
            Map.entry(NamedTextColor.DARK_RED, new int[]{170, 0, 0}),
            Map.entry(NamedTextColor.DARK_PURPLE, new int[]{170, 0, 170}),
            Map.entry(NamedTextColor.GOLD, new int[]{255, 170, 0}),
            Map.entry(NamedTextColor.GRAY, new int[]{170, 170, 170}),
            Map.entry(NamedTextColor.DARK_GRAY, new int[]{85, 85, 85}),
            Map.entry(NamedTextColor.BLUE, new int[]{85, 85, 255}),
            Map.entry(NamedTextColor.GREEN, new int[]{85, 255, 85}),
            Map.entry(NamedTextColor.AQUA, new int[]{85, 255, 255}),
            Map.entry(NamedTextColor.RED, new int[]{255, 85, 85}),
            Map.entry(NamedTextColor.LIGHT_PURPLE, new int[]{255, 85, 255}),
            Map.entry(NamedTextColor.YELLOW, new int[]{255, 255, 85}),
            Map.entry(NamedTextColor.WHITE, new int[]{255, 255, 255})
    );

    private BedrockConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts an Adventure Component to a Bedrock-compatible legacy string.
 *
 * <p>This method strips unsupported features (click events, hover events) and converts
     * the component to legacy § color codes.
     *
     * @param component the component to convert
     * @return the legacy string representation
     */
    @NotNull
    public static String toLegacyString(@NotNull Component component) {
        Component stripped = stripUnsupportedFormatting(component);
        return LEGACY_SERIALIZER.serialize(stripped);
    }

    /**
     * Converts an Adventure Component to a Bedrock-compatible legacy string with hex color handling.
     *
     * @param component the component to convert
     * @param fallback  the hex color fallback strategy
     * @param mode      the format mode
     * @return the legacy string representation
     */
    @NotNull
    public static String toLegacyString(@NotNull Component component, 
                                        @NotNull HexColorFallback fallback,
                                        @NotNull BedrockFormatMode mode) {
        Component stripped = stripUnsupportedFormatting(component);
        
        if (mode == BedrockFormatMode.MODERN) {
            // Modern mode preserves hex colors
            return LEGACY_SERIALIZER.serialize(stripped);
        }
        
        // Conservative mode - convert hex colors based on fallback strategy
        return convertHexColors(LEGACY_SERIALIZER.serialize(stripped), fallback);
    }

    /**
     * Converts a MiniMessage string to a Bedrock-compatible legacy string.
     *
     * @param miniMessage the MiniMessage string to convert
     * @return the legacy string representation
     */
    @NotNull
    public static String fromMiniMessage(@NotNull String miniMessage) {
        return fromMiniMessage(miniMessage, HexColorFallback.NEAREST_LEGACY, BedrockFormatMode.CONSERVATIVE);
    }

    /**
     * Converts a MiniMessage string to a Bedrock-compatible legacy string with configuration.
     *
     * @param miniMessage the MiniMessage string to convert
     * @param fallback    the hex color fallback strategy
     * @param mode        the format mode
     * @return the legacy string representation
     */
    @NotNull
    public static String fromMiniMessage(@NotNull String miniMessage,
                                         @NotNull HexColorFallback fallback,
                                         @NotNull BedrockFormatMode mode) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return "";
        }

        String processed = miniMessage;
        
        // Handle gradients in conservative mode
        if (mode == BedrockFormatMode.CONSERVATIVE) {
            processed = handleGradients(processed, fallback);
        }

        try {
            Component component = MINI_MESSAGE.deserialize(processed);
            return toLegacyString(component, fallback, mode);
        } catch (Exception e) {
            // Fallback: strip all tags and return plain text
            return PLAIN_SERIALIZER.serialize(MINI_MESSAGE.deserialize(miniMessage));
        }
    }

    /**
     * Converts a hex color string to the nearest legacy color code.
 *
 * <p>Uses Euclidean distance in RGB color space to find the closest match.
     *
     * @param hexColor the hex color (with or without # prefix, supports #RRGGBB and RRGGBB)
     * @return the legacy color code (e.g., "§6" for gold)
     */
    @NotNull
    public static String hexToNearestLegacy(@NotNull String hexColor) {
        String hex = hexColor.replace("#", "").replace("§", "").replace("&", "");
        if (hex.length() != 6) {
            return "§f"; // Default to white for invalid input
        }

        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);

            NamedTextColor nearest = findNearestLegacyColor(r, g, b);
            return "§" + getLegacyCode(nearest);
        } catch (NumberFormatException e) {
            return "§f"; // Default to white for invalid hex
        }
    }

    /**
     * Converts a hex color to a grayscale legacy color based on luminance.
     *
     * @param hexColor the hex color
     * @return the grayscale legacy color code (§f, §7, or §8)
     */
    @NotNull
    public static String hexToGrayscale(@NotNull String hexColor) {
        String hex = hexColor.replace("#", "").replace("§", "").replace("&", "");
        if (hex.length() != 6) {
            return "§f";
        }

        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);

            // Calculate luminance using standard formula
            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;

            if (luminance > 170) {
                return "§f"; // White
            } else if (luminance > 85) {
                return "§7"; // Gray
            } else {
                return "§8"; // Dark gray
            }
        } catch (NumberFormatException e) {
            return "§f";
        }
    }

    /**
     * Strips all unsupported Bedrock formatting from a component.
 *
 * <p>Removes click events, hover events, insertion text, and custom fonts.
     *
     * @param component the component to strip
     * @return a new component with unsupported formatting removed
     */
    @NotNull
    public static Component stripUnsupportedFormatting(@NotNull Component component) {
        return stripRecursive(component);
    }

    /**
     * Extracts the dominant color from a gradient for conservative mode fallback.
     *
     * @param startHex the starting hex color
     * @param endHex   the ending hex color
     * @return the dominant (average) color as a hex string
     */
    @NotNull
    public static String extractDominantColor(@NotNull String startHex, @NotNull String endHex) {
        try {
            String start = startHex.replace("#", "");
            String end = endHex.replace("#", "");

            int r1 = Integer.parseInt(start.substring(0, 2), 16);
            int g1 = Integer.parseInt(start.substring(2, 4), 16);
            int b1 = Integer.parseInt(start.substring(4, 6), 16);

            int r2 = Integer.parseInt(end.substring(0, 2), 16);
            int g2 = Integer.parseInt(end.substring(2, 4), 16);
            int b2 = Integer.parseInt(end.substring(4, 6), 16);

            // Average the colors
            int r = (r1 + r2) / 2;
            int g = (g1 + g2) / 2;
            int b = (b1 + b2) / 2;

            return String.format("#%02X%02X%02X", r, g, b);
        } catch (Exception e) {
            return "#FFFFFF";
        }
    }

    // ========== Private Helper Methods ==========

    private static Component stripRecursive(@NotNull Component component) {
        TextComponent.Builder builder;

        if (component instanceof TextComponent textComponent) {
            builder = Component.text()
                    .content(textComponent.content());
        } else {
            builder = Component.text()
                    .content(PLAIN_SERIALIZER.serialize(component));
        }

        // Preserve all colors including hex - Geyser handles them properly
        if (component.color() != null) {
            builder.color(component.color());
        }

        // Preserve text decorations
        for (TextDecoration decoration : TextDecoration.values()) {
            TextDecoration.State state = component.decoration(decoration);
            if (state != TextDecoration.State.NOT_SET) {
                builder.decoration(decoration, state);
            }
        }

        // Note: Click events, hover events, insertion, and custom fonts are NOT preserved
        // as Bedrock clients don't support them

        // Recursively process children
        for (Component child : component.children()) {
            builder.append(stripRecursive(child));
        }

        return builder.build();
    }

    private static NamedTextColor findNearestLegacyColor(int r, int g, int b) {
        NamedTextColor nearest = NamedTextColor.WHITE;
        double minDistance = Double.MAX_VALUE;

        for (Map.Entry<NamedTextColor, int[]> entry : LEGACY_COLOR_RGB.entrySet()) {
            int[] rgb = entry.getValue();
            double distance = colorDistance(r, g, b, rgb[0], rgb[1], rgb[2]);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = entry.getKey();
            }
        }

        return nearest;
    }

    private static double colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static String getLegacyCode(NamedTextColor color) {
        return switch (color.toString()) {
            case "black" -> "0";
            case "dark_blue" -> "1";
            case "dark_green" -> "2";
            case "dark_aqua" -> "3";
            case "dark_red" -> "4";
            case "dark_purple" -> "5";
            case "gold" -> "6";
            case "gray" -> "7";
            case "dark_gray" -> "8";
            case "blue" -> "9";
            case "green" -> "a";
            case "aqua" -> "b";
            case "red" -> "c";
            case "light_purple" -> "d";
            case "yellow" -> "e";
            default -> "f"; // white
        };
    }

    private static String convertHexColors(@NotNull String text, @NotNull HexColorFallback fallback) {
        String result = text;

        // Handle MiniMessage hex format <#RRGGBB>
        Matcher hexMatcher = HEX_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            String replacement = switch (fallback) {
                case STRIP -> "";
                case NEAREST_LEGACY -> hexToNearestLegacy(hex);
                case GRAYSCALE -> hexToGrayscale(hex);
            };
            hexMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        hexMatcher.appendTail(sb);
        result = sb.toString();

        // Handle legacy hex format &#RRGGBB or §#RRGGBB
        Matcher ampHexMatcher = AMPERSAND_HEX_PATTERN.matcher(result);
        sb = new StringBuffer();
        while (ampHexMatcher.find()) {
            String hex = ampHexMatcher.group(1);
            String replacement = switch (fallback) {
                case STRIP -> "";
                case NEAREST_LEGACY -> hexToNearestLegacy(hex);
                case GRAYSCALE -> hexToGrayscale(hex);
            };
            ampHexMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        ampHexMatcher.appendTail(sb);

        return sb.toString();
    }

    private static String handleGradients(@NotNull String text, @NotNull HexColorFallback fallback) {
        Matcher gradientMatcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (gradientMatcher.find()) {
            String content = gradientMatcher.group(1);
            // For conservative mode, just use the content without gradient
            // The color will be handled by the hex fallback
            gradientMatcher.appendReplacement(sb, Matcher.quoteReplacement(content));
        }
        gradientMatcher.appendTail(sb);
        
        return sb.toString();
    }
}
